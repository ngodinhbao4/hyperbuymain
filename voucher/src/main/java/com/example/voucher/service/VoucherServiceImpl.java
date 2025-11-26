package com.example.voucher.service;

import com.example.voucher.dto.VoucherResponse;
import com.example.voucher.entity.UserVoucher;
import com.example.voucher.entity.Voucher;
import com.example.voucher.repository.UserVoucherRepository;
import com.example.voucher.repository.VoucherRepository;
import com.example.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;



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
    public UserVoucher issueVoucherToUser(String userId, String code) {
        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Voucher không tồn tại"));

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

    // ✅ TÍNH GIẢM GIÁ KHI THANH TOÁN
    @Override
    public BigDecimal calculateDiscount(String userId, String code, BigDecimal orderAmount) {
        // Mặc định không giảm
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        UserVoucher userVoucher = userVoucherRepository
                .findByUserIdAndVoucher_CodeAndUsedFalse(userId, code)
                .orElse(null);

        if (userVoucher == null) {
            // user không có voucher này hoặc đã dùng
            return BigDecimal.ZERO;
        }

        Voucher voucher = userVoucher.getVoucher();

        // Kiểm tra trạng thái
        if (voucher.getStatus() != Voucher.Status.ACTIVE) {
            return BigDecimal.ZERO;
        }

        // Kiểm tra thời gian hiệu lực
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            return BigDecimal.ZERO;
        }
        if (voucher.getEndDate() != null && now.isAfter(voucher.getEndDate())) {
            return BigDecimal.ZERO;
        }

        // Kiểm tra số lượng còn lại
        Integer quantity = voucher.getQuantity() != null ? voucher.getQuantity() : 0;
        Integer used = voucher.getUsed() != null ? voucher.getUsed() : 0;
        if (quantity > 0 && used >= quantity) {
            return BigDecimal.ZERO; // hết lượt
        }

        // Tính số tiền giảm
        Double discountValue = voucher.getDiscountValue();
        if (discountValue == null || discountValue <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;

        if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType())) {
            // giảm theo %
            BigDecimal percent = BigDecimal.valueOf(discountValue)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discount = orderAmount.multiply(percent);
        } else if ("AMOUNT".equalsIgnoreCase(voucher.getDiscountType())) {
            // giảm số tiền cố định
            discount = BigDecimal.valueOf(discountValue);
        } else {
            // loại không hợp lệ
            return BigDecimal.ZERO;
        }

        // Không được giảm quá tổng đơn
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        // Làm tròn 2 chữ số thập phân
        discount = discount.setScale(2, RoundingMode.HALF_UP);

        return discount;
    }

    // ✅ GỌI SAU KHI ORDER ĐÃ THANH TOÁN THÀNH CÔNG
    @Override
    public void markVoucherUsed(String userId, String code) {
        UserVoucher userVoucher = userVoucherRepository
                .findByUserIdAndVoucher_CodeAndUsedFalse(userId, code)
                .orElse(null);

        if (userVoucher == null) {
            return;
        }

        Voucher voucher = userVoucher.getVoucher();

        // đánh dấu userVoucher
        userVoucher.setUsed(true);
        userVoucherRepository.save(userVoucher);

        // tăng used của voucher
        Integer used = voucher.getUsed() != null ? voucher.getUsed() : 0;
        voucher.setUsed(used + 1);

        // nếu đã dùng >= quantity => có thể set INACTIVE
        Integer quantity = voucher.getQuantity() != null ? voucher.getQuantity() : 0;
        if (quantity > 0 && voucher.getUsed() >= quantity) {
            voucher.setStatus(Voucher.Status.INACTIVE);
        }

        voucherRepository.save(voucher);
    }

    public List<VoucherResponse> getAvailableVouchers(String userId) {
    LocalDateTime now = LocalDateTime.now();

    List<UserVoucher> userVouchers = userVoucherRepository.findByUserIdAndUsedFalse(userId);

    return userVouchers.stream()
            .map(UserVoucher::getVoucher)
            .filter(v -> 
                    // voucher đã bắt đầu (startDate <= now hoặc null thì bỏ qua)
                    (v.getStartDate() == null || !v.getStartDate().isAfter(now)) &&
                    // voucher chưa hết hạn (endDate >= now hoặc null thì bỏ qua)
                    (v.getEndDate() == null || !v.getEndDate().isBefore(now)) &&
                    // chỉ lấy voucher đang ACTIVE
                    v.getStatus() == Voucher.Status.ACTIVE
            )
            .map(v -> new VoucherResponse(
                    v.getId(),
                    v.getCode(),
                    // discountType: enum/String → toString để FE dễ dùng
                    v.getDiscountType() != null ? v.getDiscountType().toString() : null,
                    // discountValue trong DTO là Integer → giữ nguyên
                    v.getDiscountValue(),
                    v.getStartDate(),
                    v.getEndDate(),
                    v.getQuantity(),
                    // status: enum → chuyển sang String
                    v.getStatus() != null ? v.getStatus().toString() : null
            ))
            .collect(Collectors.toList());
}

}
