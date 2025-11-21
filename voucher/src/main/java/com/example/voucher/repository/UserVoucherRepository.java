package com.example.voucher.repository;

import com.example.voucher.entity.UserVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    List<UserVoucher> findByUserId(String userId);
}
