package com.addzero.addl.util.meta

class IllegalFileFormatException(type: String) : RuntimeException("Type <$type> of file is not a valid format")