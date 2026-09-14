package com.commercehub.backend.wallet.mapper;

import com.commercehub.backend.wallet.dto.response.HoldReleaseResponse;
import com.commercehub.backend.wallet.dto.response.WalletResponse;
import com.commercehub.backend.wallet.dto.response.WalletTransactionResponse;
import com.commercehub.backend.wallet.dto.response.WithdrawalResponse;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.WalletTransaction;
import com.commercehub.backend.wallet.entity.Withdrawal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    WalletResponse toWalletResponse(Wallet wallet);

    WalletTransactionResponse toTransactionResponse(WalletTransaction transaction);

    @Mapping(target = "status", expression = "java(withdrawal.getStatus().name())")
    @Mapping(target = "approvedByUsername", source = "approvedBy.username")
    @Mapping(target = "processorUsername", source = "processor.username")
    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal);

    HoldReleaseResponse toHoldReleaseResponse(HoldRelease holdRelease);
}
