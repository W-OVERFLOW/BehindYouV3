package xyz.qalcyo.behindyou.utils

import gg.essential.api.EssentialAPI
import xyz.qalcyo.behindyou.BehindYou
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import xyz.qalcyo.mango.Multithreading
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.util.*
import java.util.concurrent.Future

object Updater {
    var latestTag = ""
    var shouldUpdate = false
    var shouldShowNotification = false
    var updateUrl = ""
    var updateFuture: Future<*>? = null

    /**
     * Stolen from SimpleTimeChanger under AGPLv3
     * https://github.com/My-Name-Is-Jeff/SimpleTimeChanger/blob/master/LICENSE
     */
    fun update() {
        updateFuture = Multithreading.submit {
            val latestRelease =
                APIUtil.getJSONResponse("https://api.github.com/repos/Qalcyo/${BehindYou.ID}/releases/latest")
            latestTag = latestRelease.get("tag_name").asString

            val currentVersion = BehindYouVersion.CURRENT
            latestTag = latestRelease.get("tag_name").asString.substringAfter("v")
            val latestVersion = BehindYouVersion.fromString(latestTag)
            if (currentVersion < latestVersion) {
                updateUrl = latestRelease["assets"].asJsonArray[0].asJsonObject["browser_download_url"].asString
                shouldUpdate = true
                shouldShowNotification = true
            }
        }
    }

    /**
     * Adapted from RequisiteLaunchwrapper under LGPLv3
     * https://github.com/Qalcyo/RequisiteLaunchwrapper/blob/main/LICENSE
     */
    fun download(url: String, file: File): Boolean {
        if (file.exists()) return true
        var newUrl = url
        newUrl = newUrl.replace(" ", "%20")
        val downloadClient: HttpClient =
            HttpClientBuilder.create().setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(10000).build())
                .build()
        try {
            FileOutputStream(file).use { fileOut ->
                val downloadResponse: HttpResponse = downloadClient.execute(HttpGet(newUrl))
                val buffer = ByteArray(1024)
                var read: Int
                while (downloadResponse.entity.content.read(buffer).also { read = it } > 0) {
                    fileOut.write(buffer, 0, read)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * Adapted from Skytils under AGPLv3
     * https://github.com/Skytils/SkytilsMod/blob/1.x/LICENSE.md
     */
    fun addShutdownHook() {
        EssentialAPI.getShutdownHookUtil().register {
            println("Deleting old ${BehindYou.NAME} jar file...")
            try {
                val runtime = getJavaRuntime()
                if (System.getProperty("os.name").lowercase(Locale.ENGLISH).contains("mac")) {
                    val sipStatus = Runtime.getRuntime().exec("csrutil status")
                    sipStatus.waitFor()
                    if (!sipStatus.inputStream.use { it.bufferedReader().readText() }
                            .contains("System Integrity Protection status: disabled.")) {
                        println("SIP is NOT disabled, opening Finder.")
                        Desktop.getDesktop().open(BehindYou.jarFile.parentFile)
                    }
                }
                println("Using runtime $runtime")
                val file = File("config/Qalcyo/Deleter-1.2.jar")
                println("\"$runtime\" -jar \"${file.absolutePath}\" \"${BehindYou.jarFile.absolutePath}\"")
                if (System.getProperty("os.name").lowercase(Locale.ENGLISH).containsAny("linux", "unix")) {
                    println("On Linux, giving Deleter jar execute permissions...")
                    Runtime.getRuntime()
                        .exec("chmod +x \"${file.absolutePath}\"")
                }
                Runtime.getRuntime()
                    .exec("\"$runtime\" -jar \"${file.absolutePath}\" \"${BehindYou.jarFile.absolutePath}\"")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Gets the current Java runtime being used.
     * @link https://stackoverflow.com/a/47925649
     */
    @Throws(IOException::class)
    fun getJavaRuntime(): String {
        val os = System.getProperty("os.name")
        val java = "${System.getProperty("java.home")}${File.separator}bin${File.separator}${
            if (os != null && os.lowercase().startsWith("windows")) "java.exe" else "java"
        }"
        if (!File(java).isFile) {
            throw IOException("Unable to find suitable java runtime at $java")
        }
        return java
    }

    /**
     * Adapted from Kotlin under Apache License 2.0
     * https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt
     */
    class BehindYouVersion(val major: Int, val minor: Int, val patch: Int, val beta: Int) : Comparable<BehindYouVersion> {

        private val version = versionOf(major, minor, patch, beta)

        private fun versionOf(major: Int, minor: Int, patch: Int, beta: Int): Int {
            require(major in 0..MAX_COMPONENT_VALUE && minor in 0..MAX_COMPONENT_VALUE && patch in 0..MAX_COMPONENT_VALUE && beta in 0..MAX_COMPONENT_VALUE) {
                "Version components are out of range: $major.$minor.$patch-beta$beta"
            }
            return major.shl(16) + minor.shl(8) + patch + beta
        }

        override fun toString(): String = "$major.$minor.$patch-beta$beta"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            val otherVersion = (other as? BehindYouVersion) ?: return false
            return this.version == otherVersion.version
        }

        override fun hashCode(): Int = version

        override fun compareTo(other: BehindYouVersion): Int = version - other.version

        companion object {
            const val MAX_COMPONENT_VALUE = 255
            val regex = Regex("^(?<major>[0|1-9\\d*])\\.(?<minor>[0|1-9\\d*])\\.(?<patch>[0|1-9\\d*])(?:-beta)?(?<beta>.*)?\$")

            val CURRENT: BehindYouVersion = fromString(BehindYou.VERSION)

            fun fromString(version: String): BehindYouVersion {
                val match = regex.matchEntire(version)
                if (match != null) {
                    return BehindYouVersion(match.groups["major"]!!.value.toInt(), match.groups["minor"]!!.value.toInt(), match.groups["patch"]!!.value.toInt(), if (match.groups["beta"]?.value.isNullOrBlank()) 0 else match.groups["beta"]!!.value.toInt())
                } else {
                    throw ParseException("The string ($version) provided did not match the BehindYou Version regex!", -1)
                }
            }
        }
    }

}