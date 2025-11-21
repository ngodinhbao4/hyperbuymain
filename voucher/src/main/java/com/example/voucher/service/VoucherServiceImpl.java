package com.example.voucher.service;

import com.example.voucher.entity.UserVoucher;
import com.example.voucher.entity.Voucher;
import com.example.voucher.repository.UserVoucherRepository;
import com.example.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    @Override
    public Voucher createVoucher(Voucher voucher) {
        return voucherRepository.save(voucher);
    }

    @Override
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    @Override
    @Transactional
    public UserVoucher issueVoucherToUser(String userId, String code) {
        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Voucher not found"));

        if (voucher.getUsed() >= voucher.getQuantity()) {
            throw new RuntimeException("Voucher hết số lượng");
        }

        if (voucher.getEndDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Voucher đã hết hạn");
        }

        voucher.setUsed(voucher.getUsed() + 1);
        voucherRepository.save(voucher);

        UserVoucher uv = UserVoucher.builder()
                .userId(userId)
                .voucher(voucher)
                .build();

        return userVoucherRepository.save(uv);
    }

    @Override
    public List<UserVoucher> getUserVouchers(String userId) {
        return userVoucherRepository.findByUserId(userId);
    }
}
