import java.nio.file.*;
import java.sql.*;

/** Tiện ích chạy file SQL migration khi máy không có psql. */
public class RunSql {
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5678/commercehub_db");
        String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "123");

        String sql = Files.readString(Path.of(args[0]));
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement()) {
            st.execute(sql);
            System.out.println("OK: migration applied");
        }
    }
}
