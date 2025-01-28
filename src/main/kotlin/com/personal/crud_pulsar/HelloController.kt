package com.personal.crud_pulsar

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@CrossOrigin(origins = ["http://localhost:3000"])
@RestController
@RequestMapping("/users")
class HelloController(val userRepository: UserRepository, val eventPublisher: EventPublisher, val auditLogService: AuditLogService) {
    private val logger = LoggerFactory.getLogger(HelloController::class.java)

    private fun createResponse(status: HttpStatus, errCode: Int, description: String, data: Any? = null): ResponseEntity<Map<String, Any>> {
        val response = mutableMapOf<String, Any>("statusCode" to errCode, "description" to description)
        data?.let { response["data"] = it }
        return ResponseEntity.status(status).body(response)
    }

    @PostMapping
    fun createUser(@RequestBody newUser: User): ResponseEntity<Map<String, Any>> {
        logger.info("Attempting to create user with ID: ${newUser.id}")
        if (userRepository.existsById(newUser.id)) {
            logger.warn("User ID ${newUser.id} already exists.")
            publishAuditEvent("Create User", "User ID already exists", 409)
            throw ConflictException("User ID already exists")
        }
        return try {
            userRepository.save(newUser)
            eventPublisher.publishPlainMessage("User created: ${newUser.id}")
            eventPublisher.publishRawMessage(Customer(newUser.id.toInt(), newUser.name))
            logger.info("User with ID: ${newUser.id} created successfully.")
            publishAuditEvent("Create User", "User created successfully", 201)
            createResponse(HttpStatus.CREATED, 201, "User created successfully")
        } catch (ex: Exception) {
            logger.error("Internal server error while creating user", ex)
            publishAuditEvent("Create User", "Internal server error", 500)
            throw InternalServerErrorException("Internal server error")
        }
    }

    @GetMapping
    fun listUsers(): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching all users")
        return try {
            val users = userRepository.findAll()
            eventPublisher.publishPlainMessage("Fetched all users")
            publishAuditEvent("Fetch Users", "Users fetched successfully", 200)
            createResponse(HttpStatus.OK, 200, "Users fetched successfully", users)
        } catch (ex: Exception) {
            logger.error("Internal server error while fetching users", ex)
            publishAuditEvent("Fetch Users", "Internal server error", 500)
            throw InternalServerErrorException("Internal server error")
        }
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching user with ID: ${id}")
        return userRepository.findById(id).map {
            eventPublisher.publishPlainMessage("Fetched user: ${id}")
            logger.info("User with ID: ${id} found.")
            publishAuditEvent("Fetch User", "User found", 200)
            createResponse(HttpStatus.OK, 200, "User found", it)
        }.orElseThrow {
            logger.warn("User with ID: ${id} not found.")
            publishAuditEvent("Fetch User", "User not found", 404)
            UserNotFoundException("User not found")
        }
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        logger.info("Attempting to delete user with ID: ${id}")
        if (!userRepository.existsById(id)) {
            logger.warn("User with ID: ${id} not found.")
            publishAuditEvent("Delete User", "User not found", 404)
            throw UserNotFoundException("User not found")
        }
        return try {
            userRepository.deleteById(id)
            eventPublisher.publishPlainMessage("Deleted user: ${id}")
            logger.info("User with ID: ${id} deleted successfully.")
            publishAuditEvent("Delete User", "User deleted successfully", 204)
            createResponse(HttpStatus.NO_CONTENT, 204, "User deleted successfully")
        } catch (ex: Exception) {
            logger.error("Internal server error while deleting user", ex)
            publishAuditEvent("Delete User", "Internal server error", 500)
            throw InternalServerErrorException("Internal server error")
        }
    }

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody updatedUser: User): ResponseEntity<Map<String, Any>> {
        logger.info("Attempting to update user with ID: ${id}")
        if (!userRepository.existsById(id)) {
            logger.warn("User with ID: ${id} not found.")
            publishAuditEvent("Update User", "User not found", 404)
            throw UserNotFoundException("User not found")
        }
        return try {
            val existingUser = userRepository.findById(id).get()
            val newUser = existingUser.copy(
                name = updatedUser.name,
                age = updatedUser.age,
                address = updatedUser.address,
                phoneNumber = updatedUser.phoneNumber
            )
            userRepository.save(newUser)
            eventPublisher.publishPlainMessage("Updated user: ${id}")
            eventPublisher.publishRawMessage(Customer(newUser.id.toInt(), newUser.name))
            logger.info("User with ID: ${id} updated successfully.")
            publishAuditEvent("Update User", "User updated successfully", 200)
            createResponse(HttpStatus.OK, 200, "User updated successfully")
        } catch (ex: Exception) {
            logger.error("Internal server error while updating user", ex)
            publishAuditEvent("Update User", "Internal server error", 500)
            throw InternalServerErrorException("Internal server error")
        }
    }

    @GetMapping("/search")
    fun searchUsersByName(@RequestParam name: String): ResponseEntity<Map<String, Any>> {
        logger.info("Searching users with name: $name")
        return try {
            val users = userRepository.findAll().filter { it.name.contains(name, ignoreCase = true) }
            if (users.isNotEmpty()) {
                logger.info("Found ${users.size} users matching the name: $name")
                publishAuditEvent("Search Users", "Found ${users.size} users with the name: $name", 200)
                createResponse(HttpStatus.OK, 200, "Users found", users)
            } else {
                logger.warn("No users found matching the name: $name")
                publishAuditEvent("Search Users", "No users found with the name: $name", 404)
                throw UserNotFoundException("No users found with the given name")
            }
        } catch (ex: Exception) {
            logger.error("Internal server error while searching users by name", ex)
            publishAuditEvent("Search Users", "Internal server error", 500)
            throw InternalServerErrorException("Internal server error")
        }
    }


    private fun publishAuditEvent(action: String, description: String, statusCode: Int?) {
        // Ensure that statusCode is never null, provide a default value if necessary
        val validatedStatusCode = statusCode ?: 500 // Default to 500 if null

        // Publish to Pulsar
        val auditMessage = AuditMessage(action = action, description = description, statusCode = validatedStatusCode)
        val auditMessageJson = ObjectMapper().writeValueAsString(auditMessage)
        eventPublisher.publishPlainMessage(auditMessageJson)

        // Save to PostgreSQL
        val auditLog = AuditLog(
            action = action,
            description = description,
            statusCode = validatedStatusCode, // Use the validated statusCode
            timestamp = LocalDateTime.now()
        )
        auditLogService.saveAuditLog(auditLog)
        logger.info("Audit log saved to database: $auditLog")
    }


    data class AuditMessage(
        val action: String,
        val description: String,
        val statusCode: Int
    )
}
