package com.universal.virtual.common.result

class VirtualException(val error: VirtualError) : RuntimeException(error.message, error.cause)
