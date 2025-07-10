package com.example.docprocessor.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "voter_id_data")
data class VoterIdData(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // This is now nullable
    @Column(name = "name", nullable = true)
    var name: String?,

    // This is now nullable
    @Column(name = "gender", nullable = true, length = 10)
    var gender: String?,

    // This is now nullable
    @Column(name = "dob", nullable = true)
    var dob: LocalDate?,

    // This remains the single required, unique field
    @Column(name = "voter_id_number", unique = true, nullable = false, length = 20)
    var voterIdNumber: String,

    @Column(name = "image_path", nullable = false)
    var imagePath: String,

    @Column(name = "uploaded_by", nullable = false)
    var uploadedBy: String,

    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: LocalDateTime = LocalDateTime.now()
)