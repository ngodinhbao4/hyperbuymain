package com.example.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class SellerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String storeName;
    String businessLicense;
    @OneToOne(fetch = FetchType.EAGER)
    User user;
}
