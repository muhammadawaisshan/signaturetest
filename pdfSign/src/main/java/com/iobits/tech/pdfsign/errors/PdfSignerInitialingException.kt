package com.iobits.tech.pdfsign.errors


public class PdfSignerInitialingException internal constructor(private val errorMessage: String) :
    Throwable(errorMessage)