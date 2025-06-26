package com.example.docprocessor.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "passport_data")
data class PassportData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "passport_number", unique = true, nullable = false, length = 20)
    var passportNumber: String,

    @Column(name = "country_code", nullable = false, length = 3)
    var countryCode: String,

    @Column(name = "surname", nullable = false)
    var surname: String,

    @Column(name = "given_names", nullable = false)
    var givenNames: String,

    @Column(name = "nationality", nullable = false, length = 3)
    var nationality: String,

    @Column(name = "date_of_birth", nullable = false)
    var dateOfBirth: LocalDate,

    @Column(name = "sex", nullable = false, length = 1)
    var sex: String,

    @Column(name = "expiration_date", nullable = false)
    var expirationDate: LocalDate,

    @Column(name = "image_path", nullable = false)
    var imagePath: String,

    @Column(name = "uploaded_by", nullable = false)
    var uploadedBy: String,

    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: LocalDateTime = LocalDateTime.now()
)