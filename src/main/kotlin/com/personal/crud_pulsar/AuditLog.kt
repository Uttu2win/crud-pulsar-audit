package com.personal.crud_pulsar

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "action")
    val action: String,

    @Column(name = "description")
    val description: String,

    @Column(name = "status_code")
    val statusCode: Int,

    @Column(name = "timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now()
)
