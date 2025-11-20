#!/bin/bash

# Скрипт для сравнения PasswordHack и John the Ripper на macOS M1 Pro
# Устанавливает John the Ripper, собирает проект и тестирует хеши

set -e  # Остановка при ошибках

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Переменные
JAR_FILE="build/libs/PasswordHack-1.0-SNAPSHOT.jar"
RESULTS_FILE="benchmark_results.txt"
TEMP_DIR="benchmark_temp"
JOHN_HASH_FILE="$TEMP_DIR/john_hashes.txt"

# Создаем временную директорию
mkdir -p "$TEMP_DIR"
chmod 755 "$TEMP_DIR"

# Массив для хранения результатов
declare -A results_ph_time
declare -A results_john_time
declare -A results_ph_pass
declare -A results_john_pass

# Функция для вывода заголовка
print_header() {
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}\n"
}

# Функция для проверки и установки Homebrew
check_homebrew() {
    print_header "Проверка Homebrew"
    if ! command -v brew &> /dev/null; then
        echo -e "${YELLOW}Homebrew не найден. Установка Homebrew...${NC}"
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    else
        echo -e "${GREEN}✓ Homebrew установлен${NC}"
    fi
}

# Функция для проверки и установки John the Ripper
install_john() {
    print_header "Проверка John the Ripper"
    if ! command -v john &> /dev/null; then
        echo -e "${YELLOW}John the Ripper не найден. Установка...${NC}"
        if brew list john-jumbo &>/dev/null 2>&1; then
            echo -e "${GREEN}✓ John the Ripper (jumbo) уже установлен${NC}"
        else
            echo -e "${YELLOW}Установка john-jumbo...${NC}"
            brew install john-jumbo 2>/dev/null || {
                echo -e "${YELLOW}Попытка установки john (стандартная версия)...${NC}"
                brew install john
            }
        fi
    else
        echo -e "${GREEN}✓ John the Ripper установлен${NC}"
    fi
    
    # Проверяем версию
    john --version 2>/dev/null | head -n1 || echo -e "${YELLOW}Предупреждение: не удалось получить версию John${NC}"
}

# Функция для сборки проекта
build_project() {
    print_header "Сборка проекта PasswordHack"
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${YELLOW}JAR файл не найден. Запуск сборки...${NC}"
        if [ -f "gradlew" ]; then
            chmod +x gradlew 2>/dev/null || true
            ./gradlew clean build jar
        elif [ -f "gradlew.bat" ]; then
            chmod +x gradlew.bat 2>/dev/null || true
            ./gradlew clean build jar
        else
            echo -e "${RED}Ошибка: gradlew не найден${NC}"
            exit 1
        fi
    fi
    
    if [ -f "$JAR_FILE" ]; then
        echo -e "${GREEN}✓ Проект собран: $JAR_FILE${NC}"
    else
        echo -e "${RED}Ошибка: не удалось собрать проект${NC}"
        exit 1
    fi
}

# Функция для запуска PasswordHack и измерения времени
run_passwordhack() {
    local hash="$1"
    local max_length="$2"
    local min_length="${3:-1}"
    local use_gpu="${4:-false}"
    
    local start_time=$(date +%s.%N)
    
    local gpu_flag=""
    if [ "$use_gpu" = "true" ]; then
        gpu_flag="-gpu"
    fi
    
    # Запускаем и захватываем вывод
    local output=$(java -jar "$JAR_FILE" -hash="$hash" -length="$max_length" -minLength="$min_length" $gpu_flag 2>&1)
    local end_time=$(date +%s.%N)
    
    # Парсим время выполнения из вывода (совместимо с macOS grep)
    # Формат: "⏱️  Execution time: 1.23 seconds"
    local time_match=$(echo "$output" | sed -n 's/.*Execution time: \([0-9.]*\).*/\1/p' | head -n1 || echo "")
    
    # Извлекаем найденный пароль (совместимо с macOS grep)
    # Формат: "✅ Password found: password123"
    local password=$(echo "$output" | sed -n 's/.*Password found: \([^ ]*\).*/\1/p' | head -n1 || echo "")
    
    # Вычисляем время если не нашли в выводе
    local elapsed_time=0
    if [ -n "$time_match" ]; then
        elapsed_time="$time_match"
    else
        elapsed_time=$(echo "$end_time - $start_time" | bc -l 2>/dev/null || echo "0")
    fi
    
    echo "$elapsed_time|$password"
}

# Функция для запуска John the Ripper и измерения времени
run_john() {
    local hash="$1"
    local hash_type="$2"  # sha1, md5, bcrypt, argon2
    local max_length="${3:-8}"
    local min_length="${4:-1}"
    
    # Очищаем предыдущие хеши
    rm -f "$JOHN_HASH_FILE"
    touch "$JOHN_HASH_FILE"
    chmod 644 "$JOHN_HASH_FILE"
    echo "$hash" > "$JOHN_HASH_FILE"
    
    local start_time=$(date +%s.%N)
    
    # Определяем формат для John
    local john_format=""
    case "$hash_type" in
        "sha1")
            john_format="Raw-SHA1"
            ;;
        "md5")
            john_format="Raw-MD5"
            ;;
        "bcrypt")
            john_format="bcrypt"
            ;;
        "argon2")
            john_format="argon2"
            ;;
    esac
    
    # Для простых хешей используем меньший таймаут
    local timeout_seconds=1800  # 30 минут максимум
    case "$hash" in
        *"7c4a8d09ca3762af61e59520943dc26494f8941b"*|*"e10adc3949ba59abbe56e057f20f883e"*)  # Легкие
            timeout_seconds=120
            ;;
        *"d0be2dc421be4fcd0172e5afceea3970e2f3d940"*|*"1f3870be274f6c49b3e31a0c6728957f"*)  # Средние
            timeout_seconds=600
            ;;
    esac
    
    local password=""
    local john_output_file="$TEMP_DIR/john_output_$$.txt"
    touch "$john_output_file"
    chmod 644 "$john_output_file"
    
    # Запускаем John
    timeout $timeout_seconds john --format="$john_format" --incremental=ASCII --min-length=$min_length --max-length=$max_length "$JOHN_HASH_FILE" > "$john_output_file" 2>&1 || true
    
    # Пытаемся получить найденный пароль
    password=$(john --show --format="$john_format" "$JOHN_HASH_FILE" 2>/dev/null | grep -v "^$" | head -n1 | cut -d: -f2 | xargs || echo "")
    
    local end_time=$(date +%s.%N)
    
    # Вычисляем время выполнения
    local elapsed_time=$(echo "$end_time - $start_time" | bc -l 2>/dev/null || echo "0")
    
    # Очищаем файлы
    rm -f "$john_output_file"
    rm -f "$JOHN_HASH_FILE"
    john --format="$john_format" "$JOHN_HASH_FILE" 2>/dev/null || true
    
    echo "$elapsed_time|$password"
}

# Функция для форматирования времени
format_time() {
    local seconds="$1"
    
    # Проверяем, является ли число валидным
    if ! echo "$seconds" | grep -qE '^[0-9]+\.?[0-9]*$'; then
        echo "N/A"
        return
    fi
    
    local result=$(echo "$seconds" | bc -l 2>/dev/null || echo "0")
    
    if (( $(echo "$result < 0.001" | bc -l 2>/dev/null || echo "1") )); then
        printf "%.3f" "$result"
    elif (( $(echo "$result < 0.01" | bc -l 2>/dev/null || echo "1") )); then
        printf "%.3f" "$result"
    elif (( $(echo "$result < 60" | bc -l 2>/dev/null || echo "1") )); then
        printf "%.2f" "$result"
    elif (( $(echo "$result < 3600" | bc -l 2>/dev/null || echo "1") )); then
        local minutes=$(echo "scale=1; $result / 60" | bc -l 2>/dev/null || echo "0")
        printf "%.1f мин" "$minutes"
    else
        local hours=$(echo "scale=2; $result / 3600" | bc -l 2>/dev/null || echo "0")
        printf "%.2f ч" "$hours"
    fi
}

# Функция для обновления таблицы результатов
update_table() {
    local algorithm="$1"
    local complexity="$2"
    local ph_time="$3"
    local john_time="$4"
    local ph_password="$5"
    local john_password="$6"
    
    # Сохраняем результаты в массивы
    results_ph_time["${algorithm}_${complexity}"]="$ph_time"
    results_john_time["${algorithm}_${complexity}"]="$john_time"
    results_ph_pass["${algorithm}_${complexity}"]="$ph_password"
    results_john_pass["${algorithm}_${complexity}"]="$john_password"
}

# Функция для вывода финальной таблицы
print_final_table() {
    # Заголовок таблицы
    printf "${CYAN}%-15s │ %-20s │ %-15s │ %-12s │ %-10s │ %-12s │ %-11s${NC}\n" \
        "Пароль" "Ваш инструмент" "johnny" "SHA-1" "MD5" "bcrypt" "Argon2"
    echo "────────────────────────────┼──────────────────────┼─────────────────┼──────────────┼───────────┼──────────────┼────────────"
    
    # Определяем строки для каждой сложности
    local complexities=("easy" "medium" "hard" "very_hard")
    local complexity_names=("Легкий       " "Средний      " "Сложный      " "Очень сложный")
    
    for i in "${!complexities[@]}"; do
        local complexity="${complexities[$i]}"
        local row_name="${complexity_names[$i]}"
        
        # Получаем значения для каждого алгоритма
        local sha1_ph_time=$(format_time "${results_ph_time[SHA1_${complexity}]:-0}")
        local sha1_john_time=$(format_time "${results_john_time[SHA1_${complexity}]:-0}")
        local sha1_ph_pass="${results_ph_pass[SHA1_${complexity}]:-}"
        local sha1_john_pass="${results_john_pass[SHA1_${complexity}]:-}"
        
        local md5_ph_time=$(format_time "${results_ph_time[MD5_${complexity}]:-0}")
        local md5_john_time=$(format_time "${results_john_time[MD5_${complexity}]:-0}")
        local md5_ph_pass="${results_ph_pass[MD5_${complexity}]:-}"
        local md5_john_pass="${results_john_pass[MD5_${complexity}]:-}"
        
        local bcrypt_ph_time=$(format_time "${results_ph_time[bcrypt_${complexity}]:-0}")
        local bcrypt_john_time=$(format_time "${results_john_time[bcrypt_${complexity}]:-0}")
        local bcrypt_ph_pass="${results_ph_pass[bcrypt_${complexity}]:-}"
        local bcrypt_john_pass="${results_john_pass[bcrypt_${complexity}]:-}"
        
        local argon2_ph_time=$(format_time "${results_ph_time[Argon2_${complexity}]:-0}")
        local argon2_john_time=$(format_time "${results_john_time[Argon2_${complexity}]:-0}")
        local argon2_ph_pass="${results_ph_pass[Argon2_${complexity}]:-}"
        local argon2_john_pass="${results_john_pass[Argon2_${complexity}]:-}"
        
        # Добавляем > если пароль не найден
        if [ -z "$sha1_ph_pass" ]; then
            sha1_ph_time="> $sha1_ph_time"
        fi
        if [ -z "$sha1_john_pass" ]; then
            sha1_john_time="> $sha1_john_time"
        fi
        if [ -z "$md5_ph_pass" ]; then
            md5_ph_time="> $md5_ph_time"
        fi
        if [ -z "$md5_john_pass" ]; then
            md5_john_time="> $md5_john_time"
        fi
        if [ -z "$bcrypt_ph_pass" ]; then
            bcrypt_ph_time="> $bcrypt_ph_time"
        fi
        if [ -z "$bcrypt_john_pass" ]; then
            bcrypt_john_time="> $bcrypt_john_time"
        fi
        if [ -z "$argon2_ph_pass" ]; then
            argon2_ph_time="> $argon2_ph_time"
        fi
        if [ -z "$argon2_john_pass" ]; then
            argon2_john_time="> $argon2_john_time"
        fi
        
        # Общая колонка "Ваш инструмент" и "johnny" - берем среднее по всем алгоритмам
        local ph_common=""
        local john_common=""
        
        # Вычисляем среднее время по всем алгоритмам для этой сложности
        local ph_times=()
        local john_times=()
        
        if [ -n "${results_ph_time[SHA1_${complexity}]}" ]; then
            ph_times+=("${results_ph_time[SHA1_${complexity}]}")
            john_times+=("${results_john_time[SHA1_${complexity}]}")
        fi
        if [ -n "${results_ph_time[MD5_${complexity}]}" ]; then
            ph_times+=("${results_ph_time[MD5_${complexity}]}")
            john_times+=("${results_john_time[MD5_${complexity}]}")
        fi
        if [ -n "${results_ph_time[bcrypt_${complexity}]}" ]; then
            ph_times+=("${results_ph_time[bcrypt_${complexity}]}")
            john_times+=("${results_john_time[bcrypt_${complexity}]}")
        fi
        if [ -n "${results_ph_time[Argon2_${complexity}]}" ]; then
            ph_times+=("${results_ph_time[Argon2_${complexity}]}")
            john_times+=("${results_john_time[Argon2_${complexity}]}")
        fi
        
        if [ ${#ph_times[@]} -gt 0 ]; then
            # Вычисляем среднее
            local ph_sum=0
            local john_sum=0
            for t in "${ph_times[@]}"; do
                ph_sum=$(echo "$ph_sum + $t" | bc -l 2>/dev/null || echo "0")
            done
            for t in "${john_times[@]}"; do
                john_sum=$(echo "$john_sum + $t" | bc -l 2>/dev/null || echo "0")
            done
            local ph_avg=$(echo "scale=2; $ph_sum / ${#ph_times[@]}" | bc -l 2>/dev/null || echo "0")
            local john_avg=$(echo "scale=2; $john_sum / ${#john_times[@]}" | bc -l 2>/dev/null || echo "0")
            
            ph_common=$(format_time "$ph_avg")
            john_common=$(format_time "$john_avg")
        else
            ph_common="-"
            john_common="-"
        fi
        
        # Выводим строку
        printf "%-15s │ %-20s │ %-15s │ %-12s │ %-10s │ %-12s │ %-11s\n" \
            "$row_name" "$ph_common" "$john_common" "$sha1_ph_time" "$md5_ph_time" "$bcrypt_ph_time" "$argon2_ph_time"
    done
}

# Основная функция тестирования
main() {
    print_header "Начало бенчмарка PasswordHack vs John the Ripper"
    
    # Проверки и установка
    check_homebrew
    install_john
    build_project
    
    # Проверяем наличие bc (калькулятор)
    if ! command -v bc &> /dev/null; then
        echo -e "${YELLOW}Установка bc (калькулятор)...${NC}"
        brew install bc
    fi
    
    # Проверяем наличие timeout (может отсутствовать на macOS)
    if ! command -v timeout &> /dev/null; then
        echo -e "${YELLOW}Установка coreutils для команды timeout...${NC}"
        brew install coreutils
        # Проверяем оба возможных пути для M1 и Intel Mac
        if [ -d "/opt/homebrew/opt/coreutils/libexec/gnubin" ]; then
            export PATH="/opt/homebrew/opt/coreutils/libexec/gnubin:$PATH"
        elif [ -d "/usr/local/opt/coreutils/libexec/gnubin" ]; then
            export PATH="/usr/local/opt/coreutils/libexec/gnubin:$PATH"
        fi
    fi
    
    # Массив с хешами для тестирования
    declare -A hashes=(
        # SHA-1
        ["sha1_easy"]="7c4a8d09ca3762af61e59520943dc26494f8941b"
        ["sha1_medium"]="d0be2dc421be4fcd0172e5afceea3970e2f3d940"
        ["sha1_hard"]="666846867fc5e0a46a7afc53eb8060967862f333"
        ["sha1_very_hard"]="6e157c5da4410b7e9de85f5c93026b9176e69064"
        
        # MD5
        ["md5_easy"]="e10adc3949ba59abbe56e057f20f883e"
        ["md5_medium"]="1f3870be274f6c49b3e31a0c6728957f"
        ["md5_hard"]="77892341aa9dc66e97f5c248782b5d92"
        ["md5_very_hard"]="686e697538050e4664636337cc3b834f"
        
        # bcrypt
        ["bcrypt_easy"]='$2a$10$z4u9ZkvopUiiytaNX7wfGedy9Lu2ywUxwYpbsAR5YBrAuUs3YGXdi'
        ["bcrypt_medium"]='$2a$10$26GB/T2/6aTsMkTjCgqm/.JP8SUjr32Bhfn9m9smtDiIwM4QIt2ze'
        ["bcrypt_hard"]='$2a$10$Q9M0vLLrE4/nu/9JEMXFTewB3Yr9uMdIEZ1Sgdk1NQTjHwLN0asfi'
        ["bcrypt_very_hard"]='$2a$10$yZBadi8Szw0nItV2g96P6eqctI2kbG/.mb0uD/ID9tlof0zpJLLL2'
        
        # Argon2
        ["argon2_easy"]='$argon2id$v=19$m=65536,t=3,p=2$c2FsdHNhbHQ$PUF5UxxoUY++mMekkQwFurL0ZsTtB7lelO23zcyZQ0c'
        ["argon2_medium"]='$argon2id$v=19$m=65536,t=3,p=2$c2FsdHNhbHQ$HYQwRUw9VcfkvqkUQ5ppyYPom6f/ro3ZCXYznhrYZw4'
        ["argon2_hard"]='$argon2id$v=19$m=65536,t=3,p=2$c2FsdHNhbHQ$9asGA7Xv3vQBz7Yyh4/Ntw0GQgOg8R6OWolOfRETrEg'
        ["argon2_very_hard"]='$argon2id$v=19$m=65536,t=3,p=2$c2FsdHNhbHQ$+smq45/czydGj0lYNdZVXF++FOXJwrkXt6VUIcEauvo'
    )
    
    # Определяем параметры длины для каждого уровня сложности
    declare -A length_params=(
        ["easy"]="3"
        ["medium"]="5"
        ["hard"]="7"
        ["very_hard"]="10"
    )
    
    # Порядок тестирования: от простого к сложному, сначала быстрые алгоритмы
    local test_order=(
        "sha1_easy" "sha1_medium" 
        "md5_easy" "md5_medium"
        "sha1_hard" "md5_hard"
        "bcrypt_easy"
        "sha1_very_hard" "md5_very_hard"
        "bcrypt_medium"
        "argon2_easy"
        "bcrypt_hard"
        "argon2_medium"
        "bcrypt_very_hard"
        "argon2_hard"
        "argon2_very_hard"
    )
    
    print_header "Начало тестирования"
    echo -e "${YELLOW}Внимание: некоторые тесты могут занять продолжительное время${NC}\n"
    
    local test_count=0
    local total_tests=${#test_order[@]}
    
    for test_key in "${test_order[@]}"; do
        test_count=$((test_count + 1))
        
        IFS='_' read -r algorithm complexity <<< "$test_key"
        local hash="${hashes[$test_key]}"
        local max_length="${length_params[$complexity]}"
        
        # Определяем тип хеша для John
        local john_hash_type=""
        local algorithm_upper=""
        case "$algorithm" in
            "sha1") 
                john_hash_type="sha1"
                algorithm_upper="SHA1"
                ;;
            "md5") 
                john_hash_type="md5"
                algorithm_upper="MD5"
                ;;
            "bcrypt") 
                john_hash_type="bcrypt"
                algorithm_upper="bcrypt"
                ;;
            "argon2") 
                john_hash_type="argon2"
                algorithm_upper="Argon2"
                ;;
        esac
        
        echo -e "\n${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${YELLOW}Тест $test_count/$total_tests: ${algorithm^^} - $complexity (макс. длина: $max_length)${NC}"
        echo -e "${YELLOW}Хеш: ${hash:0:60}...${NC}"
        echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
        
        # Запускаем PasswordHack
        echo -e "${BLUE}[1/2] Запуск PasswordHack...${NC}"
        local ph_result=$(run_passwordhack "$hash" "$max_length" "1" "false")
        local ph_time=$(echo "$ph_result" | cut -d'|' -f1)
        local ph_password=$(echo "$ph_result" | cut -d'|' -f2)
        
        if [ -n "$ph_password" ]; then
            echo -e "${GREEN}✓ PasswordHack: время = ${ph_time}с, пароль найден: $ph_password${NC}"
        else
            echo -e "${YELLOW}✗ PasswordHack: время = ${ph_time}с, пароль не найден${NC}"
        fi
        
        sleep 1
        
        # Запускаем John the Ripper
        echo -e "${BLUE}[2/2] Запуск John the Ripper...${NC}"
        local john_result=$(run_john "$hash" "$john_hash_type" "$max_length" "1")
        local john_time=$(echo "$john_result" | cut -d'|' -f1)
        local john_password=$(echo "$john_result" | cut -d'|' -f2)
        
        if [ -n "$john_password" ]; then
            echo -e "${GREEN}✓ John the Ripper: время = ${john_time}с, пароль найден: $john_password${NC}"
        else
            echo -e "${YELLOW}✗ John the Ripper: время = ${john_time}с, пароль не найден${NC}"
        fi
        
        # Сохраняем результаты
        update_table "$algorithm_upper" "$complexity" "$ph_time" "$john_time" "$ph_password" "$john_password"
        
        # Обновляем таблицу на экране после каждого теста
        echo -e "\n${CYAN}═══════════════════════════════════════════════════════${NC}"
        echo -e "${CYAN}Текущие результаты:${NC}"
        echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
        print_final_table
        
        echo -e "${GREEN}✓ Тест $test_count/$total_tests завершен${NC}\n"
        sleep 2
    done
    
    # Выводим финальную таблицу
    echo -e "\n"
    print_header "Финальная таблица результатов"
    print_final_table
    
    # Сохраняем результаты в файл
    {
        echo "Таблица результатов: PasswordHack vs John the Ripper"
        echo "═══════════════════════════════════════════════════════"
        echo ""
        print_final_table
    } > "$RESULTS_FILE"
    
    # Очистка
    rm -rf "$TEMP_DIR"
    
    print_header "Бенчмарк завершен"
    echo -e "${GREEN}Результаты сохранены в: $RESULTS_FILE${NC}"
}

# Запуск основной функции
main "$@"