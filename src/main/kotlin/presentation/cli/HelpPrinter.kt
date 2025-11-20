package presentation.cli

object HelpPrinter {
    fun print() {
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
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="7c4a8d09ca3762af61e59520943dc26494f8941b"
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="7c4a8d09ca3762af61e59520943dc26494f8941b" -length=10
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="\$2a\$10\$..." -length=6 -threads=16
    """.trimIndent())
    }
}

