package com.example.docprocessor.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "voter_id_data")
data class VoterIdData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "dob", nullable = false)
    var dob: LocalDate,
    @Column(name = "voter_id_number", unique = true, nullable = false, length = 20)
    var voterIdNumber: String,
    @Column(name = "image_path", nullable = false)
    var imagePath: String,
    @Column(name = "uploaded_by", nullable = false)
    var uploadedBy: String,
    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: LocalDateTime = LocalDateTime.now()
)