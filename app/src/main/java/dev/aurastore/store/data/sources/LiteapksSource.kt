package dev.aurastore.store.data.sources

import dev.aurastore.store.data.Api
import dev.aurastore.store.data.PostSummary

/**
 * The catalogue the app was already built on, exposed through the same [Source] interface as the
 * web sources so the picker can list them together. Nothing changes in how it is fetched: this is
 * `Api.search` / `Api.post`, and its files are the same `version_downloads` entries the app page
 * already offers (now signed and referer-carrying, see [dev.aurastore.store.data.Hosts]).
 */
object LiteapksSource : Source {

    override val id = SourceId.LITEAPKS

    override suspend fun search(query: String): List<SourceApp> {
        // The ranked answer first — it is the best few and carries the full row — then the first page
        // of the paged one, which is where the rest of the matches are. The two overlap on purpose.
        val ranked = Api.search(query)
        val paged = runCatching { Api.searchPage(query, page = 1, pageSize = 24) }.getOrNull()
            ?.items
            .orEmpty()
        return (ranked + paged).distinctBy { it.id }.map { summary -> summary.toSourceApp() }
    }

    /**
     * The catalogue's own newest posts.
     *
     * `order_by=modified` is the ordering the catalogue's own "Last update" chip uses, so the row a
     * person taps here is the same row they would have reached from that chip.
     */
    override suspend fun latest(): List<SourceApp> =
        Api.posts(mapOf("order_by" to "modified", "limit" to "12", "page" to "1"))
            .map { summary -> summary.toSourceApp() }

    /**
     * The catalogue's term tree, flattened one level.
     *
     * A parent term's own count covers only the posts tagged with the term itself, so its children are
     * listed beside it rather than under it: `Games` reports 5 while its children hold thousands.
     */
    override suspend fun categories(): List<SourceCategory> =
        Api.categories().flatMap { term ->
            listOf(term) + term.children
        }.map { term ->
            SourceCategory(
                source = SourceId.LITEAPKS,
                id = term.id.toString(),
                name = term.name,
                count = term.count,
                url = "https://liteapks.com/category/${term.slug ?: term.id}",
            )
        }

    /** The posts of one term, in the catalogue's own view ordering. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val termId = category.id.toLongOrNull() ?: return emptyList()
        val page = Api.postPage(
            mapOf(
                "taxonomy" to "category",
                "tax_ids" to termId.toString(),
                "order_by" to "post_views",
                "limit" to "20",
                "page" to "1",
            ),
            1,
        )
        return page.items.map { summary -> summary.toSourceApp() }
    }

    override suspend fun files(app: SourceApp): List<SourceFile> {
        val postId = app.cataloguePostId ?: app.key.toLongOrNull() ?: return emptyList()
        // No ABI filter: the user sees every artefact the post publishes, as on the app page.
        val detail = Api.post(postId, abi = "")
        return detail.versions.flatMap { version ->
            version.files.map { file ->
                SourceFile(
                    label = listOf(version.label, file.kind, file.abi)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    url = file.url,
                    size = file.sizeText.ifBlank { null },
                    note = file.note ?: version.note,
                )
            }
        }
    }
}

private fun PostSummary.toSourceApp() = SourceApp(
    source = SourceId.LITEAPKS,
    key = id.toString(),
    title = title,
    version = latestVersion,
    size = size,
    subtitle = listOfNotNull(publisher, genre, type).joinToString(" · ").ifBlank { null },
    iconUrl = imageUrl,
    pageUrl = "https://liteapks.com/app/$id",
    cataloguePostId = id,
)
