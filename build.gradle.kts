plugins {
    id("java")
    id ("application")
}

group = "br.com.s3tech"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    // Bibliotecas para ler ficheiros Excel nativos (.xlsx)
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // Biblioteca para manipular JSON (para a API do Sankhya)
    implementation("com.google.code.gson:gson:2.10.1")
    // ADICIONE ESTA LINHA PARA O ENVIO DE E-MAIL
    implementation("com.sun.mail:javax.mail:1.6.2")

    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20221023-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.15.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// === ADICIONE ESTE BLOCO PARA CRIAR O JAR EXECUTÁVEL (Sintaxe Kotlin DSL) ===
tasks.jar {
    manifest {
        // Verifique se o caminho da Main é este mesmo
        attributes["Main-Class"] = "br.com.s3tech.integrador.Main"
    }

    // Coleta todas as dependências (Gson, POI, JavaMail, OpenCSV)
    val dependencies = configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    from(dependencies)

    // Remove assinaturas de bibliotecas (essencial para evitar SecurityException)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 2. BLOCO APPLICATION (Aqui no final, totalmente fora do bloco plugins!)
application {
    mainClass.set("br.com.s3tech.integrador.Main")
}