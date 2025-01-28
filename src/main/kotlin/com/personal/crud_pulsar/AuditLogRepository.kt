package com.personal.crud_pulsar

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long>
