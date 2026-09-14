package com.commercehub.backend.admin.service;

import com.commercehub.backend.admin.dto.AdminResponses;
import com.commercehub.backend.common.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminResponses.Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);

        List<AdminResponses.DailyMetric> daily = jdbc.query("""
                SELECT day::date AS metric_date,
                       COALESCE((SELECT SUM(o.total_amount) FROM orders o
                           WHERE o.placed_at >= day AND o.placed_at < day + INTERVAL '1 day'
                             AND o.payment_status <> 'REFUNDED'), 0) AS gmv,
                       COALESCE((SELECT SUM(COALESCE(f.adjusted_fee_amount, f.fee_amount))
                           FROM platform_fee_ledgers f
                           WHERE f.status IN ('COLLECTED','ADJUSTED')
                             AND COALESCE(f.collected_at, f.updated_at) >= day
                             AND COALESCE(f.collected_at, f.updated_at) < day + INTERVAL '1 day'), 0) AS fee,
                       (SELECT COUNT(*) FROM orders o
                           WHERE o.placed_at >= day AND o.placed_at < day + INTERVAL '1 day'
                             AND o.payment_status <> 'REFUNDED') AS order_count
                FROM generate_series(?::date, (?::date - INTERVAL '1 day'), INTERVAL '1 day') day
                ORDER BY day
                """, (rs, rowNum) -> new AdminResponses.DailyMetric(
                rs.getDate("metric_date").toLocalDate(),
                money(rs, "gmv"), money(rs, "fee"), rs.getLong("order_count")
        ), Date.valueOf(monthStart), Date.valueOf(nextMonth));

        List<AdminResponses.LeaderboardItem> topShops = jdbc.query("""
                SELECT s.id, s.name, COALESCE(SUM(o.total_amount), 0) amount, COUNT(o.id) order_count
                FROM shops s
                JOIN orders o ON o.shop_id = s.id
                WHERE o.placed_at >= ? AND o.placed_at < ? AND o.payment_status <> 'REFUNDED'
                GROUP BY s.id, s.name ORDER BY amount DESC, order_count DESC LIMIT 5
                """, this::leaderboardRow, startOf(monthStart), startOf(nextMonth));

        List<AdminResponses.LeaderboardItem> topCategories = jdbc.query("""
                SELECT c.id, c.name, COALESCE(SUM(oi.line_total), 0) amount, COUNT(DISTINCT oi.order_id) order_count
                FROM categories c
                JOIN products p ON p.category_id = c.id
                JOIN product_variants pv ON pv.product_id = p.id
                JOIN order_items oi ON oi.product_variant_id = pv.id
                JOIN orders o ON o.id = oi.order_id
                WHERE o.placed_at >= ? AND o.placed_at < ? AND o.payment_status <> 'REFUNDED'
                GROUP BY c.id, c.name ORDER BY amount DESC, order_count DESC LIMIT 5
                """, this::leaderboardRow, startOf(monthStart), startOf(nextMonth));

        return new AdminResponses.Dashboard(
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(DISTINCT ur.user_id) FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE r.name='SELLER' AND u.status='ACTIVE'"),
                count("SELECT COUNT(*) FROM shops WHERE status='PENDING'"),
                count("SELECT COUNT(*) FROM shops WHERE status='ACTIVE'"),
                count("SELECT COUNT(*) FROM products WHERE status='ACTIVE'"),
                count("SELECT COUNT(*) FROM orders WHERE placed_at >= ? AND placed_at < ?", startOf(monthStart), startOf(nextMonth)),
                count("SELECT COUNT(*) FROM order_disputes WHERE status = 'ADMIN_REVIEW'"),
                scalarMoney("""
                        SELECT CASE WHEN COUNT(oi.id)=0 THEN 0
                            ELSE ROUND(COUNT(d.id)::numeric * 100 / COUNT(oi.id), 2) END
                        FROM order_items oi
                        LEFT JOIN order_disputes d ON d.order_item_id=oi.id
                        """),
                count("SELECT COUNT(*) FROM withdrawals WHERE status='PENDING'"),
                count("SELECT COUNT(*) FROM deposits WHERE status='REVIEW_REQUIRED'"),
                scalarMoney("SELECT COALESCE(SUM(total_amount),0) FROM orders WHERE placed_at >= ? AND placed_at < ? AND payment_status <> 'REFUNDED'", startOf(monthStart), startOf(nextMonth)),
                scalarMoney("SELECT COALESCE(SUM(COALESCE(adjusted_fee_amount, fee_amount)),0) FROM platform_fee_ledgers WHERE status IN ('COLLECTED','ADJUSTED') AND COALESCE(collected_at, updated_at) >= ? AND COALESCE(collected_at, updated_at) < ?", startOf(monthStart), startOf(nextMonth)),
                scalarMoney("SELECT COALESCE(SUM(COALESCE(adjusted_fee_amount, fee_amount)),0) FROM platform_fee_ledgers WHERE status IN ('COLLECTED','ADJUSTED')"),
                scalarMoney("SELECT COALESCE(SUM(hold_balance),0) FROM wallets"),
                daily, topShops, topCategories
        );
    }

    public PageResponse<AdminResponses.UserRow> users(String keyword, String status, String role, int page, int size) {
        SqlFilter filter = new SqlFilter(" FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id LEFT JOIN roles r ON r.id=ur.role_id WHERE 1=1 ");
        if (hasText(keyword)) filter.add(" AND (u.email ILIKE ? OR u.username ILIKE ? OR u.full_name ILIKE ?)", like(keyword), like(keyword), like(keyword));
        if (hasText(status)) filter.add(" AND u.status=?", upper(status));
        if (hasText(role)) filter.add(" AND r.name=?", upper(role));
        String select = "SELECT u.id,u.email,u.username,u.full_name,u.avatar_url,u.status,u.ban_reason,r.name role_name,u.provider,u.user_level,u.accumulated_spent,u.accumulated_earned,u.is_email_verified,u.is_phone_verified,u.created_at,u.last_active_at";
        return page(select, filter, " ORDER BY u.created_at DESC,u.id DESC", page, size, (rs, n) -> new AdminResponses.UserRow(
                rs.getLong("id"), rs.getString("email"), rs.getString("username"), rs.getString("full_name"), rs.getString("avatar_url"),
                rs.getString("status"), rs.getString("ban_reason"), rs.getString("role_name"), rs.getString("provider"), rs.getInt("user_level"),
                money(rs,"accumulated_spent"), money(rs,"accumulated_earned"), rs.getBoolean("is_email_verified"), rs.getBoolean("is_phone_verified"),
                time(rs,"created_at"), time(rs,"last_active_at")
        ));
    }

    public PageResponse<AdminResponses.ShopRow> shops(String keyword, String status, int page, int size) {
        SqlFilter filter = new SqlFilter(" FROM shops s JOIN users u ON u.id=s.owner_id WHERE 1=1 ");
        if (hasText(keyword)) filter.add(" AND (s.name ILIKE ? OR s.slug ILIKE ? OR u.username ILIKE ? OR u.email ILIKE ?)", like(keyword), like(keyword), like(keyword), like(keyword));
        if (hasText(status)) filter.add(" AND s.status=?", upper(status));
        String select = """
                SELECT s.id,s.owner_id,u.email owner_email,u.username owner_username,s.name,s.slug,
                       COALESCE(s.shop_avatar_url,u.avatar_url) avatar_url,s.status,s.total_orders,s.total_disputes,
                       s.dispute_rate,s.rating_avg,s.rating_count,s.version,s.created_at,
                       (SELECT COUNT(*) FROM products p WHERE p.shop_id=s.id AND p.status<>'DELETED') product_count,
                       (SELECT COALESCE(SUM(p.sold_count),0) FROM products p WHERE p.shop_id=s.id) sold_count
                """;
        return page(select, filter, " ORDER BY CASE WHEN s.status='PENDING' THEN 0 ELSE 1 END,s.created_at DESC,s.id DESC", page, size,
                (rs,n) -> new AdminResponses.ShopRow(rs.getLong("id"),rs.getLong("owner_id"),rs.getString("owner_email"),rs.getString("owner_username"),
                        rs.getString("name"),rs.getString("slug"),rs.getString("avatar_url"),rs.getString("status"),rs.getLong("product_count"),rs.getLong("sold_count"),
                        rs.getInt("total_orders"),rs.getInt("total_disputes"),money(rs,"dispute_rate"),money(rs,"rating_avg"),rs.getLong("rating_count"),rs.getLong("version"),time(rs,"created_at")));
    }

    public PageResponse<AdminResponses.ProductRow> products(String keyword, String status, Long shopId, Long categoryId, int page, int size) {
        SqlFilter filter = new SqlFilter(" FROM products p JOIN shops s ON s.id=p.shop_id JOIN categories c ON c.id=p.category_id WHERE p.status<>'DELETED' ");
        if (hasText(keyword)) filter.add(" AND (p.name ILIKE ? OR p.slug ILIKE ? OR s.name ILIKE ?)", like(keyword),like(keyword),like(keyword));
        if (hasText(status)) filter.add(" AND p.status=?", upper(status));
        if (shopId != null) filter.add(" AND p.shop_id=?", shopId);
        if (categoryId != null) filter.add(" AND p.category_id=?", categoryId);
        String select = """
                SELECT p.id,p.shop_id,s.name shop_name,p.category_id,c.name category_name,p.name,p.slug,p.thumbnail_url,
                       p.product_type,p.delivery_type,p.status,p.sold_count,p.created_at,p.updated_at,
                       COALESCE((SELECT SUM(pv.stock_count) FROM product_variants pv WHERE pv.product_id=p.id AND pv.status='ACTIVE'),0) stock_count,
                       (SELECT MIN(pv.price) FROM product_variants pv WHERE pv.product_id=p.id AND pv.status='ACTIVE') min_price
                """;
        return page(select, filter, " ORDER BY p.created_at DESC,p.id DESC", page,size,(rs,n)->new AdminResponses.ProductRow(
                rs.getLong("id"),rs.getLong("shop_id"),rs.getString("shop_name"),rs.getLong("category_id"),rs.getString("category_name"),
                rs.getString("name"),rs.getString("slug"),rs.getString("thumbnail_url"),rs.getString("product_type"),rs.getString("delivery_type"),
                rs.getString("status"),rs.getLong("sold_count"),rs.getLong("stock_count"),moneyNullable(rs,"min_price"),time(rs,"created_at"),time(rs,"updated_at")));
    }

    public List<AdminResponses.CategoryRow> categories() {
        return jdbc.query("""
                SELECT c.id,c.name,c.slug,c.icon_url,c.is_active,c.sort_order,c.parent_id,parent.name parent_name,
                       (SELECT COUNT(*) FROM products p WHERE p.category_id=c.id AND p.status<>'DELETED') product_count
                FROM categories c LEFT JOIN categories parent ON parent.id=c.parent_id
                ORDER BY COALESCE(c.parent_id,c.id),CASE WHEN c.parent_id IS NULL THEN 0 ELSE 1 END,c.sort_order,c.id
                """, (rs,n) -> new AdminResponses.CategoryRow(
                rs.getLong("id"),rs.getString("name"),rs.getString("slug"),rs.getString("icon_url"),rs.getBoolean("is_active"),
                rs.getInt("sort_order"),nullableLong(rs,"parent_id"),rs.getString("parent_name"),rs.getLong("product_count")
        ));
    }

    public PageResponse<AdminResponses.DepositRow> deposits(String keyword, String status, String provider, int page, int size) {
        SqlFilter filter = new SqlFilter(" FROM deposits d JOIN users u ON u.id=d.user_id WHERE 1=1 ");
        if (hasText(keyword)) filter.add(" AND (u.username ILIKE ? OR u.email ILIKE ? OR d.transaction_code ILIKE ? OR d.provider_transaction_id ILIKE ?)", like(keyword),like(keyword),like(keyword),like(keyword));
        if (hasText(status)) filter.add(" AND d.status=?", upper(status));
        if (hasText(provider)) filter.add(" AND d.provider=?", upper(provider));
        String select="SELECT d.id,d.user_id,u.username,u.email,d.amount,d.provider,d.transaction_code,d.provider_transaction_id,d.status,d.expires_at,d.paid_at,d.processed_at,d.created_at";
        return page(select,filter," ORDER BY d.created_at DESC,d.id DESC",page,size,(rs,n)->new AdminResponses.DepositRow(
                rs.getLong("id"),rs.getLong("user_id"),rs.getString("username"),rs.getString("email"),money(rs,"amount"),rs.getString("provider"),
                rs.getString("transaction_code"),rs.getString("provider_transaction_id"),rs.getString("status"),time(rs,"expires_at"),time(rs,"paid_at"),time(rs,"processed_at"),time(rs,"created_at")));
    }

    public PageResponse<AdminResponses.WithdrawalRow> withdrawals(String keyword, String status, int page, int size) {
        SqlFilter filter=new SqlFilter(" FROM withdrawals wd JOIN wallets w ON w.id=wd.wallet_id JOIN users u ON u.id=w.user_id LEFT JOIN users processor ON processor.id=wd.processor_id WHERE 1=1 ");
        if(hasText(keyword)) filter.add(" AND (u.username ILIKE ? OR u.email ILIKE ? OR wd.account_number ILIKE ? OR wd.account_name ILIKE ?)",like(keyword),like(keyword),like(keyword),like(keyword));
        if(hasText(status)) filter.add(" AND wd.status=?",upper(status));
        String select="SELECT wd.id,u.id user_id,u.username,u.email,wd.amount,wd.fee,wd.bank_name,wd.account_number,wd.account_name,wd.status,wd.admin_note,processor.username processor_username,wd.processed_at,wd.created_at";
        return page(select,filter," ORDER BY CASE WHEN wd.status='PENDING' THEN 0 ELSE 1 END,wd.created_at DESC,wd.id DESC",page,size,(rs,n)->new AdminResponses.WithdrawalRow(
                rs.getLong("id"),rs.getLong("user_id"),rs.getString("username"),rs.getString("email"),money(rs,"amount"),money(rs,"fee"),rs.getString("bank_name"),
                rs.getString("account_number"),rs.getString("account_name"),rs.getString("status"),rs.getString("admin_note"),rs.getString("processor_username"),time(rs,"processed_at"),time(rs,"created_at")));
    }

    public PageResponse<AdminResponses.WalletTransactionRow> transactions(String keyword, String type, int page, int size) {
        SqlFilter filter=new SqlFilter(" FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id LEFT JOIN users u ON u.id=w.user_id LEFT JOIN orders o ON wt.reference_type='ORDER' AND o.id=wt.reference_id LEFT JOIN deposits d ON wt.reference_type='DEPOSIT' AND d.id=wt.reference_id WHERE 1=1 ");
        if(hasText(keyword)) filter.add(" AND (u.username ILIKE ? OR u.email ILIKE ? OR CAST(wt.id AS text) ILIKE ? OR o.order_code ILIKE ? OR d.transaction_code ILIKE ?)",like(keyword),like(keyword),like(keyword),like(keyword),like(keyword));
        if(hasText(type)) filter.add(" AND wt.transaction_type=?",upper(type));
        String select="SELECT wt.id,wt.wallet_id,u.id user_id,u.username,u.email,wt.transaction_type,wt.balance_type,wt.amount,wt.balance_before,wt.balance_after,wt.reference_id,wt.reference_type,COALESCE(o.order_code,d.transaction_code,CAST(wt.reference_id AS text)) reference_code,wt.description,wt.created_at";
        return page(select,filter," ORDER BY wt.created_at DESC,wt.id DESC",page,size,(rs,n)->new AdminResponses.WalletTransactionRow(
                rs.getObject("id", UUID.class),rs.getLong("wallet_id"),nullableLong(rs,"user_id"),rs.getString("username"),rs.getString("email"),rs.getString("transaction_type"),rs.getString("balance_type"),
                money(rs,"amount"),money(rs,"balance_before"),money(rs,"balance_after"),nullableLong(rs,"reference_id"),rs.getString("reference_type"),rs.getString("reference_code"),rs.getString("description"),time(rs,"created_at")));
    }

    public PageResponse<AdminResponses.AuditLogRow> auditLogs(String keyword, String action, String targetType, int page, int size) {
        SqlFilter filter=new SqlFilter(" FROM audit_logs a JOIN users u ON u.id=a.actor_id WHERE 1=1 ");
        if(hasText(keyword)) filter.add(" AND (u.username ILIKE ? OR a.action ILIKE ? OR a.target_type ILIKE ? OR CAST(a.target_id AS text) ILIKE ?)",like(keyword),like(keyword),like(keyword),like(keyword));
        if(hasText(action)) filter.add(" AND a.action=?",upper(action));
        if(hasText(targetType)) filter.add(" AND a.target_type=?",upper(targetType));
        String select="SELECT a.id,a.actor_id,u.username actor_username,a.actor_role,a.action,a.target_type,a.target_id,a.old_value,a.new_value,a.reason,a.ip_address,a.user_agent,a.created_at";
        return page(select,filter," ORDER BY a.created_at DESC,a.id DESC",page,size,(rs,n)->new AdminResponses.AuditLogRow(
                rs.getLong("id"),rs.getLong("actor_id"),rs.getString("actor_username"),rs.getString("actor_role"),rs.getString("action"),rs.getString("target_type"),rs.getLong("target_id"),
                json(rs.getObject("old_value")),json(rs.getObject("new_value")),rs.getString("reason"),rs.getString("ip_address"),rs.getString("user_agent"),time(rs,"created_at")));
    }

    private <T> PageResponse<T> page(String select, SqlFilter filter, String orderBy, int requestedPage, int requestedSize, org.springframework.jdbc.core.RowMapper<T> mapper) {
        int page=Math.max(0,requestedPage); int size=Math.min(MAX_PAGE_SIZE,Math.max(1,requestedSize));
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+filter.sql,Long.class,filter.args.toArray());
        List<Object> args=new ArrayList<>(filter.args); args.add(size); args.add((long)page*size);
        List<T> data=jdbc.query(select+filter.sql+orderBy+" LIMIT ? OFFSET ?",mapper,args.toArray());
        return PageResponse.<T>builder().currentPage(page).pageSize(size).totalElements(total==null?0:total)
                .totalPages(total==null?0:(int)Math.ceil(total/(double)size)).data(data).build();
    }

    private AdminResponses.LeaderboardItem leaderboardRow(ResultSet rs,int n)throws SQLException{return new AdminResponses.LeaderboardItem(rs.getLong("id"),rs.getString("name"),money(rs,"amount"),rs.getLong("order_count"));}
    private long count(String sql,Object...args){Long v=jdbc.queryForObject(sql,Long.class,args);return v==null?0:v;}
    private BigDecimal scalarMoney(String sql,Object...args){BigDecimal v=jdbc.queryForObject(sql,BigDecimal.class,args);return v==null?BigDecimal.ZERO:v;}
    private static BigDecimal money(ResultSet rs,String c)throws SQLException{BigDecimal v=rs.getBigDecimal(c);return v==null?BigDecimal.ZERO:v;}
    private static BigDecimal moneyNullable(ResultSet rs,String c)throws SQLException{return rs.getBigDecimal(c);}
    private static OffsetDateTime time(ResultSet rs,String c)throws SQLException{return rs.getObject(c,OffsetDateTime.class);}
    private static Long nullableLong(ResultSet rs,String c)throws SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;}
    private static OffsetDateTime startOf(LocalDate d){return d.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();}
    private Map<String,Object> json(Object value){if(value==null)return null;try{return objectMapper.readValue(value.toString(),new TypeReference<>(){});}catch(Exception ignored){return Map.of("raw",value.toString());}}
    private static boolean hasText(String value){return value!=null&&!value.isBlank();}
    private static String upper(String value){return value.trim().toUpperCase(Locale.ROOT);}
    private static String like(String value){return "%"+value.trim()+"%";}

    private static final class SqlFilter{
        private String sql; private final List<Object> args=new ArrayList<>();
        private SqlFilter(String sql){this.sql=sql;}
        private void add(String clause,Object...values){sql+=clause;args.addAll(List.of(values));}
    }
}
