package com.example.docprocessor.repository

import com.example.docprocessor.model.VoterIdData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VoterIdDataRepository : JpaRepository<VoterIdData, Long>