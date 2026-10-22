package de.luwify.mensamd.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CompanionTest {
    private val stundenplan = CompanionApp.STUNDENPLAN
    private val source = stundenplan.source!!

    private fun release(name: String, minSdk: Int = 26, withdrawn: Boolean = false, url: String = source.apkUrl(name)) = Release(
        versionName = name,
        versionCode = Manifests.versionCode(name)!!,
        date = "2026-10-12",
        minSdk = minSdk,
        changelog = Changelog("de", "en"),
        apkUrl = url,
        sha256 = "ab".repeat(32),
        size = 1000,
        withdrawn = withdrawn,
    )

    private fun manifest(vararg releases: Release, app: String = stundenplan.packageName) =
        releases.fold(UpdateManifest(app = app)) { m, r -> m.withRelease(r) }

    private fun apk(pkg: String = stundenplan.packageName, code: Long = 10000, signers: Set<String> = stundenplan.signers) = ApkFacts(pkg, code, signers)

    private fun problems(apk: ApkFacts?, release: Release = release("1.0.0"), sha: String = "AB".repeat(32), size: Long = 1000) =
        CompanionPolicy.problems(release, sha, size, apk, stundenplan)

    @Test
    fun theBannerFollowsInstalledDownloadFailedAndHidden() {
        val b = CompanionPolicy::banner
        assertEquals(BannerState.DOWNLOAD, b(false, false, CompanionPhase.IDLE))
        assertEquals(BannerState.OPEN, b(false, true, CompanionPhase.IDLE))
        for (phase in listOf(CompanionPhase.DOWNLOADING, CompanionPhase.VERIFYING, CompanionPhase.INSTALLING)) {
            assertEquals(BannerState.BUSY, b(false, false, phase))
        }
        assertEquals(BannerState.FAILED, b(false, false, CompanionPhase.FAILED))
        assertEquals(BannerState.HIDDEN, b(true, false, CompanionPhase.IDLE))
        assertEquals(BannerState.HIDDEN, b(true, true, CompanionPhase.IDLE))
        // A running download stays visible even if the banner was hidden meanwhile; once installed it says "Open".
        assertEquals(BannerState.BUSY, b(true, false, CompanionPhase.DOWNLOADING))
        assertEquals(BannerState.OPEN, b(false, true, CompanionPhase.INSTALLING))
    }

    /** Mensa MD only: without a choice the banner shows only while Stundenplan MD isn't installed. */
    @Test
    fun withoutAChoiceTheBannerShowsOnlyWhileNotInstalled() {
        assertEquals(false, CompanionPolicy.hidden(choice = null, installed = false))
        assertEquals(true, CompanionPolicy.hidden(choice = null, installed = true))
        // The user's choice wins either way.
        assertEquals(false, CompanionPolicy.hidden(choice = false, installed = true))
        assertEquals(true, CompanionPolicy.hidden(choice = true, installed = false))
        // Installed through the banner: hidden by default once the install is done, but not while it runs.
        val hidden = CompanionPolicy.hidden(choice = null, installed = true)
        assertEquals(BannerState.HIDDEN, CompanionPolicy.banner(hidden, installed = true, phase = CompanionPhase.IDLE))
        assertEquals(BannerState.BUSY, CompanionPolicy.banner(CompanionPolicy.hidden(null, false), installed = false, phase = CompanionPhase.INSTALLING))
    }

    @Test
    fun theStundenplanSourceIsTheGithubReleaseRepo() {
        assertNotNull(source)
        assertEquals("https://github.com/Luwify/ovgu-stundenplan/releases/download/", source.downloadPrefix)
        assertEquals("https://github.com/Luwify/ovgu-stundenplan/releases/latest/download/updates.json", stundenplan.manifestUrl)
        assertEquals("de.luwify.stundenplanmd", stundenplan.packageName)
    }

    /** The pinned certificate is the one in release/signing-cert.sha256: same key as this app. */
    @Test
    fun theStundenplanCertificateIsThisAppsPin() {
        val file = listOf("../release/signing-cert.sha256", "release/signing-cert.sha256").map(::File).first { it.exists() }
        val pin = file.readLines().first { it.isNotBlank() && !it.startsWith("#") }.replace(":", "").trim().lowercase()
        assertEquals(setOf(pin), stundenplan.signers)
    }

    @Test
    fun theReleaseIsTheNewestInstallableOneOfStundenplansManifest() {
        val m = manifest(release("1.0.0"), release("1.1.0", withdrawn = true), release("1.2.0", minSdk = 99))
        assertEquals("1.0.0", CompanionPolicy.release(m, stundenplan, source, 36)?.versionName)
        // Newer one that runs: taken; withdrawn ones never.
        assertEquals("1.3.0", CompanionPolicy.release(m.withRelease(release("1.3.0")), stundenplan, source, 36)?.versionName)
        assertEquals("1.2.0", CompanionPolicy.release(m, stundenplan, source, 99)?.versionName)
        assertNull(CompanionPolicy.release(manifest(release("1.0.0", withdrawn = true)), stundenplan, source, 36))
        assertNull(CompanionPolicy.release(manifest(), stundenplan, source, 36))
    }

    @Test
    fun aManifestOfAnotherAppOrWithForeignUrlsOffersNothing() {
        assertNull(CompanionPolicy.release(manifest(release("1.0.0"), app = "de.luwify.mensamd"), stundenplan, source, 36))
        assertNull(CompanionPolicy.release(manifest(release("1.0.0", url = "https://example.com/stundenplan-md-1.0.0.apk")), stundenplan, source, 36))
        assertNull(CompanionPolicy.release(manifest(release("1.0.0", url = "http://github.com/Luwify/ovgu-stundenplan/releases/download/v1.0.0/a.apk")), stundenplan, source, 36))
        // Another repo's APK below the same host.
        assertNull(CompanionPolicy.release(manifest(release("1.0.0", url = "https://github.com/Luwify/ovgu-mensa/releases/download/v1.0.0/a.apk")), stundenplan, source, 36))
    }

    @Test
    fun theDownloadMustBeStundenplanSignedWithThePinnedKey() {
        assertEquals(emptyList<ApkProblem>(), problems(apk()))
        assertEquals(listOf(ApkProblem.SIZE), problems(apk(), size = 999))
        assertEquals(listOf(ApkProblem.HASH), problems(apk(), sha = "cd".repeat(32)))
        assertEquals(listOf(ApkProblem.UNREADABLE), problems(null))
        assertEquals(listOf(ApkProblem.PACKAGE), problems(apk(pkg = "de.luwify.mensamd")))
        assertEquals(listOf(ApkProblem.VERSION), problems(apk(code = 10100)))
        assertEquals(listOf(ApkProblem.SIGNER), problems(apk(signers = setOf("00".repeat(32)))))
        assertEquals(listOf(ApkProblem.SIGNER), problems(apk(signers = emptySet())))
        // Signed with the pinned key and another one: not the pinned set.
        assertEquals(listOf(ApkProblem.SIGNER), problems(apk(signers = stundenplan.signers + "00".repeat(32))))
    }
}
