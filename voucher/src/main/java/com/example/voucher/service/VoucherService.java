package com.example.voucher.service;

import com.example.voucher.entity.UserVoucher;
import com.example.voucher.entity.Voucher;
import java.util.List;

public interface VoucherService {
    Voucher createVoucher(Voucher voucher);
    List<Voucher> getAllVouchers();
    UserVoucher issueVoucherToUser(String userId, String code);
    List<UserVoucher> getUserVouchers(String userId);
}
