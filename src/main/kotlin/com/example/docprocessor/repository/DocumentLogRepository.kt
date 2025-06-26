package com.example.docprocessor.repository

import com.example.docprocessor.model.DocumentLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DocumentLogRepository : JpaRepository<DocumentLog, Long>