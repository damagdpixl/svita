package com.damagdpixl.svita.ui.wardrobe

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.data.PhotoHash
import com.damagdpixl.svita.data.SvitaGraph
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Пакетний імпорт на рівні ViewModel (Robolectric, справжня in-memory база за
 * ручним DI-графом): щасливий шлях (3 фото → 3 речі з правильним підтипом,
 * сезонами, статтю), шлях помилки (битий файл у списку невдалих, решта
 * створена, retry/finish), і логіка попереджень про дублікати (pHash-lite:
 * проти наявних речей та проти інших фото цієї ж партії).
 *
 * Системний Photo Picker у Robolectric не працює, тому фото додаються прямо
 * через [ImportViewModel.addPhotos] з файлових URI — як робить і продакшн
 * (та сама лямбда хешування через ContentResolver).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportBatchTest {

    private enum class Pattern { VERTICAL, HORIZONTAL, DIAGONAL }

    private lateinit var graph: SvitaGraph.Graph
    private lateinit var vm: ImportViewModel
    private val subtypeId: Long by lazy {
        runBlocking { graph.repos.taxonomy.subtypeByKey("body.t-shirt")!!.id }
    }

    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        graph = SvitaGraph.init(app, databaseName = null)
        vm = newViewModel()
    }

    @After
    fun tearDown() {
        SvitaGraph.resetForTests()
        tempFiles.forEach(File::delete)
    }

    private val tempFiles = mutableListOf<File>()

    private fun newViewModel(scope: CoroutineScope? = null): ImportViewModel = ImportViewModel(
        graph = graph,
        localeTag = "uk",
        displayNameOf = { uri -> uri.lastPathSegment?.substringBeforeLast('.') ?: "IMG" },
        hashOf = { uri -> PhotoHash.ofPickedImage(app.contentResolver, uri) },
        scopeOverride = scope,
    )

    private fun writePatternJpeg(pattern: Pattern, name: String): File {
        val side = 128
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val dark = Color.rgb(8, 8, 8)
        val light = Color.rgb(248, 248, 248)
        val pixels = IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            when (pattern) {
                Pattern.VERTICAL -> if (x < side / 2) dark else light
                Pattern.HORIZONTAL -> if (y < side / 2) dark else light
                Pattern.DIAGONAL -> if (x >= y) light else dark
            }
        }
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        val file = File(app.cacheDir, "${name}_${System.nanoTime()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        tempFiles += file
        return file
    }

    /** Виртуальний час looper завмирає: продовження корутин головного потоку
     *  прокачуємо вручну, поки умова не виконається. */
    private fun await(description: String, timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                error("Перевищено таймаут очікування: $description")
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    private suspend fun itemCount(): Int =
        graph.repos.wardrobe.observeItems(ItemFilter(includeArchived = true)).first().size

    private suspend fun items() =
        graph.repos.wardrobe.observeItems(ItemFilter(includeArchived = true)).first()

    @Test
    fun `щасливий шлях — три фото дають три речі зі спільними полями`() {
        // Патерни мають бути достатньо різними, щоб не зламатися в попередження.
        val vertical = writePatternJpeg(Pattern.VERTICAL, "Jacket1")
        val horizontal = writePatternJpeg(Pattern.HORIZONTAL, "Jacket2")
        val diagonal = writePatternJpeg(Pattern.DIAGONAL, "Jacket3")
        val hashes = runBlocking {
            listOf(vertical, horizontal, diagonal).map { file ->
                PhotoHash.ofPickedImage(app.contentResolver, Uri.fromFile(file))
            }
        }
        hashes.forEachIndexed { i, a ->
            assertNotNull("файл $i прочитано", a)
            hashes.forEachIndexed { j, b ->
                if (i != j) {
                    assertTrue(
                        "патерни $i та $j мають бути далечі за хешем",
                        PhotoHash.hammingDistance(a!!, b!!) > PhotoHash.MAX_DISTANCE_BITS,
                    )
                }
            }
        }

        vm.addPhotos(listOf(Uri.fromFile(vertical), Uri.fromFile(horizontal), Uri.fromFile(diagonal)))
        await("фото додано в партію") { vm.state.value.photos.size == 3 }
        assertTrue(vm.state.value.pendingWarnings.isEmpty())

        vm.selectSharedSubtype(subtypeId)
        vm.setSex(Sex.FEMALE)
        vm.toggleSeason(Season.WINTER) // за замовчуванням усі сезони → лишаються В/Л/О

        vm.startImport()
        await("імпорт завершено і повертаємось назад") { vm.state.value.navigateBack }

        assertEquals(0, vm.state.value.lastFailures.size)
        assertEquals(3, vm.state.value.createdCount)
        assertEquals(3, graph.importSummary.value)

        runBlocking {
            val all = items()
            assertEquals(3, all.size)
            // Назва за замовчуванням — ім'я файла без розширення (суфікс наносекунд
            // у хелпері тесту відкидаємо так само, як продакшн відкидає розширення).
            assertEquals(
                setOf("Jacket1", "Jacket2", "Jacket3"),
                all.map { it.name.substringBeforeLast('_') }.toSet(),
            )
            all.forEach { item ->
                assertEquals(subtypeId, item.subtypeId)
                assertEquals(Sex.FEMALE, item.sex)
                assertEquals(
                    setOf(Season.SPRING, Season.SUMMER, Season.AUTUMN),
                    item.seasons,
                )
            }
            val covers = graph.coverPhotos.first()
            assertEquals(3, covers.size)
            covers.values.forEach { path -> assertTrue("файл фото збережено", File(path).exists()) }
        }
    }

    @Test
    fun `шлях помилки — битий файл у невдалих решта створена`() {
        val good1 = writePatternJpeg(Pattern.VERTICAL, "Dress1")
        val good2 = writePatternJpeg(Pattern.HORIZONTAL, "Dress2")
        val broken: Uri = Uri.parse("content://nowhere/broken.jpg")

        vm.addPhotos(listOf(Uri.fromFile(good1), Uri.fromFile(good2), broken))
        await("усі три фото в партії") { vm.state.value.photos.size == 3 }

        vm.selectSharedSubtype(subtypeId)
        vm.startImport()
        await("прохід завершився з однією невдалою") { vm.state.value.lastFailures.size == 1 }

        assertEquals(ImportFailureKind.UNREADABLE_PHOTO, vm.state.value.lastFailures[0].kind)
        assertEquals(2, vm.state.value.createdCount)
        assertEquals(2, runBlocking { itemCount() })

        // Партія лишилася з невдалим фото — це і є список на retry.
        assertEquals(listOf(broken), vm.state.value.photos.map { it.uri })

        // Retry битого файлу знову невдалий, лічильник створених не росте.
        vm.startImport()
        await("повторний прохід завершився") {
            vm.state.value.lastFailures.size == 1 && vm.state.value.phase is ImportPhase.Editing
        }
        assertEquals(2, vm.state.value.createdCount)
        assertEquals(2, runBlocking { itemCount() })

        vm.finish()
        assertEquals(2, graph.importSummary.value)
        assertTrue(vm.state.value.navigateBack)
    }

    @Test
    fun `порожня назва блокує імпорт а виправлення повертає хід`() {
        val photo = writePatternJpeg(Pattern.VERTICAL, "Hat1")
        vm.addPhotos(listOf(Uri.fromFile(photo)))
        await("фото в партії") { vm.state.value.photos.size == 1 }
        vm.setPhotoName(vm.state.value.photos[0].key, "   ")
        vm.selectSharedSubtype(subtypeId)

        vm.startImport()
        assertEquals(ImportPhase.Editing, vm.state.value.phase)
        assertTrue(vm.state.value.blankNameKeys.contains(vm.state.value.photos[0].key))
        assertEquals(0, runBlocking { itemCount() })

        vm.setPhotoName(vm.state.value.photos[0].key, "Капелюх")
        vm.startImport()
        await("імпорт завершено") { vm.state.value.navigateBack }
        assertEquals(1, vm.state.value.createdCount)
        runBlocking {
            assertEquals("Капелюх", items()[0].name)
        }
    }

    @Test
    fun `дублікат наявної речі попереджає і його можна відкинути або лишити`() = runBlocking {
        // Наявна річ з фото: сід через PhotoStore, щоб хеш рахувався зі збереженого файла.
        val pattern = writePatternJpeg(Pattern.VERTICAL, "Smuga")
        val storedPath = graph.photoStore.import(Uri.fromFile(pattern))
        assertNotNull(storedPath)
        graph.repos.wardrobe.createItem(
            draft = ItemDraft(subtypeId = subtypeId, name = "Смугаста сорочка"),
            photos = listOf(storedPath!!),
        )

        vm = newViewModel() // перестворюємо, щоб ініціалізація хешів побачила річ
        await("хеші наявних речей пораховані") { vm.state.value.existingHashCount > 0 }

        val duplicate = writePatternJpeg(Pattern.VERTICAL, "SmugaCopy")
        vm.addPhotos(listOf(Uri.fromFile(duplicate)))
        await("попередження про дублікат") { vm.state.value.pendingWarnings.size == 1 }
        assertEquals("Смугаста сорочка", vm.state.value.pendingWarnings[0].existingItemName)
        assertEquals("до рішення фото не в партії", 0, vm.state.value.photos.size)

        // Відкинути → нічого не імпортується.
        vm.discardDuplicate()
        await("попередження знято") { vm.state.value.pendingWarnings.isEmpty() }
        assertEquals(0, vm.state.value.photos.size)

        // Додати ще раз і лишити → річ створюється попри попередження.
        vm.addPhotos(listOf(Uri.fromFile(duplicate)))
        await("попередження знову") { vm.state.value.pendingWarnings.size == 1 }
        vm.keepDuplicate()
        await("фото в партії") { vm.state.value.pendingWarnings.isEmpty() && vm.state.value.photos.size == 1 }

        vm.selectSharedSubtype(subtypeId)
        vm.startImport()
        await("імпорт завершено") { vm.state.value.navigateBack }
        assertEquals(2, itemCount()) // сід + «дублікат»
    }

    @Test
    fun `дублікат усередині партії теж попереджає`() {
        val first = writePatternJpeg(Pattern.VERTICAL, "Batch1")
        val second = writePatternJpeg(Pattern.VERTICAL, "Batch2")

        vm.addPhotos(listOf(Uri.fromFile(first), Uri.fromFile(second)))
        await("перше фото чисте друге — під попередженням") {
            vm.state.value.photos.size == 1 && vm.state.value.pendingWarnings.size == 1
        }
        assertNull(
            "збіг у партії не вказує на наявну річ",
            vm.state.value.pendingWarnings[0].existingItemName,
        )
    }

    @Test
    fun `без підтипу імпорт не стартує`() {
        val photo = writePatternJpeg(Pattern.VERTICAL, "Naked1")
        vm.addPhotos(listOf(Uri.fromFile(photo)))
        await("фото в партії") { vm.state.value.photos.size == 1 }

        vm.startImport()
        assertTrue(vm.state.value.subtypeMissing)
        assertEquals(ImportPhase.Editing, vm.state.value.phase)
        assertEquals(0, runBlocking { itemCount() })
    }

    @Test
    fun `повторно обране те саме фото — три записи з унікальними ключами`() {
        // Регресія: ключем запису був uri.toString(), тому [A, C, A] давало
        // два записи з одним ключем — дублікат ключа LazyColumn (креш екрана).
        val a = writePatternJpeg(Pattern.VERTICAL, "TwinA")
        val c = writePatternJpeg(Pattern.HORIZONTAL, "Other")
        val uriA = Uri.fromFile(a)

        vm.addPhotos(listOf(uriA, Uri.fromFile(c), uriA))
        await("третє фото (дублікат першого) під попередженням") {
            vm.state.value.photos.size == 2 && vm.state.value.pendingWarnings.size == 1
        }

        vm.keepDuplicate()
        await("усі три записи в партії") {
            vm.state.value.photos.size == 3 && vm.state.value.pendingWarnings.isEmpty()
        }
        assertEquals(
            "кожен запис має власний ключ",
            3,
            vm.state.value.photos.map { it.key }.distinct().size,
        )

        vm.selectSharedSubtype(subtypeId)
        vm.startImport()
        await("імпорт завершено") { vm.state.value.navigateBack }
        assertEquals(0, vm.state.value.lastFailures.size)
        assertEquals(3, runBlocking { itemCount() })
    }

    @Test
    fun `скасування посеред проходу зупиняє обробку без хибних невдалих`() {
        val passScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        vm = newViewModel(passScope)
        try {
            val p1 = writePatternJpeg(Pattern.VERTICAL, "Cancel1")
            val p2 = writePatternJpeg(Pattern.HORIZONTAL, "Cancel2")
            val p3 = writePatternJpeg(Pattern.DIAGONAL, "Cancel3")
            vm.addPhotos(listOf(Uri.fromFile(p1), Uri.fromFile(p2), Uri.fromFile(p3)))
            await("партія з трьох фото") { vm.state.value.photos.size == 3 }

            vm.selectSharedSubtype(subtypeId)
            vm.startImport()
            await("перша річ створена") {
                (vm.state.value.phase as? ImportPhase.Importing)?.created == 1
            }

            passScope.cancel()
            // Прокачуємо looper: жодне з двох фото, що лишилися, не має
            // «дійти» до бази чи списку невдалих.
            repeat(5) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(50)
            }

            assertEquals(1, runBlocking { itemCount() })
            assertTrue(
                "скасування не публікує хибних UNREADABLE_PHOTO",
                vm.state.value.lastFailures.isEmpty(),
            )
            assertTrue(
                "прохід заморожено на момент скасування",
                vm.state.value.phase is ImportPhase.Importing,
            )
        } finally {
            passScope.cancel()
        }
    }
}
