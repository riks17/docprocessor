package com.example.docprocessor.repository

import com.example.docprocessor.model.PanData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PanDataRepository : JpaRepository<PanData, Long>