/*
 *  Copyright (c) 2022~2023 chr_56
 */

package player.phonograph.mechanism.tag

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.generic.AbstractTag
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.aiff.AiffTag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.*
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.Mp4FieldKey
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.Mp4TagField
import org.jaudiotagger.tag.mp4.field.Mp4TagBinaryField
import org.jaudiotagger.tag.mp4.field.Mp4TagCoverField
import org.jaudiotagger.tag.mp4.field.Mp4TagRawBinaryField
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.mp4.field.Mp4TagTextField
import org.jaudiotagger.tag.wav.WavTag
import player.phonograph.model.TagData
import player.phonograph.model.TagData.BinaryData
import player.phonograph.model.TagData.EmptyData
import player.phonograph.model.TagData.ErrData
import player.phonograph.model.TagData.MultipleData
import player.phonograph.model.TagData.TextData
import player.phonograph.util.reportError


fun readAllTags(audioFile: AudioFile): Map<String, TagData> {
    val items: Map<String, TagData> = try {
        when (val tag = audioFile.tag) {
            is AbstractID3v2Tag -> ID3v2Readers.ID3v2Reader.read(tag)
            is AiffTag          -> ID3v2Readers.AiffTagReader.read(tag)
            is WavTag           -> ID3v2Readers.WavTagReader.read(tag)
            is ID3v11Tag        -> ID3v1TagReaders.ID3v11TagReader.read(tag)
            is ID3v1Tag         -> ID3v1TagReaders.ID3v1TagReader.read(tag)
            is FlacTag          -> FlacTagReader.read(tag)
            is Mp4Tag           -> Mp4TagReader.read(tag)
            is AbstractTag      -> SimpleKeyValueReader.read(tag)
            else                -> emptyMap()
        }
    } catch (e: Exception) {
        reportError(e, "TagReader", "Failed to read all tags for ${audioFile.file.absolutePath}")
        emptyMap()
    }
    return items
}

sealed interface TagReader<T : Tag> {
    fun read(tag: T): Map<String, TagData>
}

object ID3v1TagReaders {

    private fun readID3v1Tag(tag: ID3v1Tag): Map<String, TagData> {
        return listOf(
            (tag.title as TagTextField),
            (tag.artist as TagTextField),
            (tag.album as TagTextField),
            (tag.genre as TagTextField),
            (tag.year as TagTextField),
            (tag.comment as TagTextField)
        ).associate {
            (it.id ?: "") to TextData(it.content ?: "")
        }
    }

    private fun readID3v11Tag(tag: ID3v11Tag): Map<String, TagData> {
        val track = tag.track as TagTextField
        return readID3v1Tag(tag) + mapOf(Pair(track.id, TextData(track.content)))
    }

    object ID3v1TagReader : TagReader<ID3v1Tag> {
        override fun read(tag: ID3v1Tag): Map<String, TagData> = readID3v1Tag(tag)
    }

    object ID3v11TagReader : TagReader<ID3v11Tag> {
        override fun read(tag: ID3v11Tag): Map<String, TagData> = readID3v11Tag(tag)
    }

}

object ID3v2Readers {

    object ID3v2Reader : TagReader<AbstractID3v2Tag> {

        override fun read(tag: AbstractID3v2Tag): Map<String, TagData> {
            return tag.frameMap
                .mapKeys { (key, frame) ->
                    val name = when (tag) {
                        is ID3v24Tag -> ID3v24FieldKey.values().firstOrNull { key == it.frameId }?.name
                        is ID3v23Tag -> ID3v23FieldKey.values().firstOrNull { key == it.frameId }?.name
                        is ID3v22Tag -> ID3v22FieldKey.values().firstOrNull { key == it.frameId }?.name
                        else         -> null
                    }
                    val frames = when (tag) {
                        is ID3v24Tag -> ID3v24Frames.getInstanceOf()
                        is ID3v23Tag -> ID3v23Frames.getInstanceOf()
                        is ID3v22Tag -> ID3v22Frames.getInstanceOf()
                        else         -> null
                    }
                    if (frames != null) {
                        val description = frames.idToValueMap.getOrDefault(key, "<Err: failed to process key>")
                        "[$key]${name.orEmpty()}($description)"
                    } else if (name != null) {
                        "[$key]$name"
                    } else {
                        key
                    }
                }
                .mapValues { (key, data) ->
                    when (data) {
                        is TagField -> {
                            preprocessTagField(data) {
                                if (data is AbstractID3v2Frame) {
                                    parseID3v2Frame(data)
                                } else {
                                    TextData(data.rawContent.toString())
                                }
                            }
                        }

                        is List<*>  -> {
                            data.map { item ->
                                if (item is TagField)
                                    preprocessTagField(item) {
                                        if (it is AbstractID3v2Frame) {
                                            parseID3v2Frame(it)
                                        } else {
                                            TextData(it.rawContent.toString())
                                        }
                                    }
                                else
                                    TextData(item.toString())
                            }.let { MultipleData(it) }
                        }

                        else        -> TextData(data.toString())
                    }
                }
        }

        private fun parseID3v2Frame(frame: AbstractID3v2Frame): TagData {
            return try {
                val text = when (val frameBody = frame.body) {
                    is FrameBodyTXXX -> "${frameBody.description}:\n\t${frameBody.userFriendlyValue}"
                    else             -> frameBody.userFriendlyValue
                }
                TextData(text)
            } catch (e: Exception) {
                reportError(e, "readID3v2Tags", "Failed to read $frame")
                ErrData("Failed to read $frame")
            }
        }
    }

    private fun readId3SupportingTag(tag: Id3SupportingTag): Map<String, TagData> = ID3v2Reader.read(tag.iD3Tag)

    object AiffTagReader : TagReader<AiffTag> {
        override fun read(tag: AiffTag): Map<String, TagData> =
            if (tag.isExistingId3Tag) {
                readId3SupportingTag(tag)
            } else {
                emptyMap()
            }
    }

    object WavTagReader : TagReader<WavTag> {
        override fun read(tag: WavTag): Map<String, TagData> =
            if (tag.isExistingId3Tag) {
                readId3SupportingTag(tag)
            } else {
                emptyMap()
            }
    }

}


object FlacTagReader : TagReader<FlacTag> {
    override fun read(tag: FlacTag): Map<String, TagData> = SimpleKeyValueReader.read(tag.vorbisCommentTag)
}


object SimpleKeyValueReader : TagReader<AbstractTag> {
    override fun read(tag: AbstractTag): Map<String, TagData> {
        val mappedFields: Map<String, List<TagField>> = tag.mappedFields
        return mappedFields.mapValues { entry ->
            entry.value.map { tagField ->
                preprocessTagField(tagField) {
                    when (it) {
                        is TagTextField -> TextData(it.content)
                        else            -> TextData(it.rawContent.toString())
                    }
                }
            }.let { MultipleData(it) }
        }
    }
}

object Mp4TagReader : TagReader<Mp4Tag> {
    override fun read(tag: Mp4Tag): Map<String, TagData> {
        val fields = tag.all.filterIsInstance<Mp4TagField>()
        val keys = Mp4FieldKey.values()
        return fields.associate { field ->
            val key = run {
                val fieldKey = keys.firstOrNull { field.id == it.fieldName }
                if (fieldKey != null) {
                    "[${fieldKey.fieldName}]${fieldKey.name} ${fieldKey.identifier.orEmpty()}"
                } else {
                    "${field.id}(${field.fieldType.let { "${it.name}<${it.fileClassId}>" }})"
                }
            }
            when (field) {
                is Mp4TagCoverField      -> key to TextData(field.toString())
                is Mp4TagBinaryField     -> key to BinaryData
                is Mp4TagReverseDnsField -> field.descriptor to TextData(field.content)
                is Mp4TagTextField       -> key to TextData(field.content)
                is Mp4TagRawBinaryField  -> key to BinaryData
                else                     -> key to ErrData("Unknown: $field")
            }
        }
    }
}

private inline fun <T : TagField> preprocessTagField(
    frame: T,
    block: (frame: T) -> TagData,
): TagData =
    when {
        frame.isBinary -> BinaryData
        frame.isEmpty  -> EmptyData
        else           -> block(frame)
    }