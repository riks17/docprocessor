package com.example.docprocessor.repository

import com.example.docprocessor.model.PassportData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PassportDataRepository : JpaRepository<PassportData, Long>