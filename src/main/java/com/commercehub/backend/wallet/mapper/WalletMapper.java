package com.commercehub.backend.wallet.mapper;

import com.commercehub.backend.wallet.dto.response.WalletResponse;
import com.commercehub.backend.wallet.dto.response.WalletTransactionResponse;
import com.commercehub.backend.wallet.dto.response.WithdrawalResponse;
import com.commercehub.backend.wallet.dto.response.HoldReleaseResponse;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.WalletTransaction;
import com.commercehub.backend.wallet.entity.Withdrawal;
import com.commercehub.backend.wallet.entity.HoldRelease;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    // Wallet → WalletResponse
    WalletResponse toWalletResponse(Wallet wallet);

    // WalletTransaction → WalletTransactionResponse
    WalletTransactionResponse toTransactionResponse(WalletTransaction transaction);

    // Withdrawal → WithdrawalResponse
    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal);

    // HoldRelease → HoldReleaseResponse
    HoldReleaseResponse toHoldReleaseResponse(HoldRelease holdRelease);
}
