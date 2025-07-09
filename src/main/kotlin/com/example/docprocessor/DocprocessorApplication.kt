package com.example.docprocessor

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.core.context.SecurityContextHolder

@SpringBootApplication
class DocprocessorApplication {
	@PostConstruct
	fun init() {
		// THE FIX: Tell Spring Security to use a context that can be passed to child threads.
		// This is essential for async/await and coroutine-based operations.
		SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
	}
}

fun main(args: Array<String>) {
	runApplication<DocprocessorApplication>(*args)
}