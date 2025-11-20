package ru.cherenkov.presentation.cli

object HelpPrinter {
    fun printHelp() {
        val dollar = '$'
        println("""
        PasswordHack - Быстрый подбор паролей
        
        Использование:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="<hash>" [опции]
        
        Опции:
          -hash=<hash>          Хэш для подбора (обязательно)
          -length=<n>            Максимальная длина пароля (по умолчанию: 8)
          -minLength=<n>         Минимальная длина пароля (по умолчанию: 1)
          -threads=<n>           Количество потоков (по умолчанию: количество ядер CPU)
          -gpu                   Использовать GPU ускорение (если доступно, только для MD5/SHA-1)
          -help                  Показать эту справку
        
        Примеры:
          # MD5/SHA-1 хэши (Linux/Mac/Windows):
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="7c4a8d09ca3762af61e59520943dc26494f8941b"
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="e10adc3949ba59abbe56e057f20f883e" -length=10
          
          # Bcrypt хэши:
          # В PowerShell используйте одинарные кавычки:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash='${dollar}2a${dollar}10${dollar}z4u9ZkvopUiiytaNX7wfGedy9Lu2ywUxwYpbsAR5YBrAuUs3YGXdi'
          # Или экранируйте $ символом обратной кавычки:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="`${dollar}2a`${dollar}10`${dollar}z4u9ZkvopUiiytaNX7wfGedy9Lu2ywUxwYpbsAR5YBrAuUs3YGXdi"
          # В Linux/Mac используйте двойные кавычки с экранированием:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="\${dollar}2a\${dollar}10\${dollar}z4u9ZkvopUiiytaNX7wfGedy9Lu2ywUxwYpbsAR5YBrAuUs3YGXdi"
    """.trimIndent())
    }
}

