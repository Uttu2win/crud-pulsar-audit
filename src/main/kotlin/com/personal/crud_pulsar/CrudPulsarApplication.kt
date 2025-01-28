package com.personal.crud_pulsar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.pulsar.annotation.EnablePulsar
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@EnablePulsar
@EnableRetry
class CrudPulsarApplication

fun main(args: Array<String>) {
	runApplication<CrudPulsarApplication>(*args)
}
