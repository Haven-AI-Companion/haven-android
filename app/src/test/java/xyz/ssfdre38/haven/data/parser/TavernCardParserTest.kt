package xyz.ssfdre38.haven.data.parser

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class TavernCardParserTest {

    @Test
    fun testParseCard() {
        val file = File("C:\\Users\\admin\\.gemini\\antigravity-cli\\brain\\7fc576a4-6219-44d9-943d-c34bafe00e88\\scratch\\card.png")
        println("Card file exists: ${file.exists()}")
        
        val rawJson = TavernCardParser.parseRawJson(FileInputStream(file))
        println("rawJson is null? ${rawJson == null}")
        if (rawJson != null) {
            println("rawJson length: ${rawJson.length}")
            println("rawJson preview: ${rawJson.take(300)}")
        }

        val charaData = TavernCardParser.parse(FileInputStream(file))
        println("charaData is null? ${charaData == null}")
        if (charaData != null) {
            println("charaData Name: ${charaData.name}")
        }
        
        assertNotNull(rawJson)
        assertNotNull(charaData)
    }

    @Test
    fun testParseCard2() {
        val file = File("C:\\Users\\admin\\.gemini\\antigravity-cli\\brain\\7fc576a4-6219-44d9-943d-c34bafe00e88\\scratch\\card2.png")
        println("Card2 file exists: ${file.exists()}")
        
        val rawJson = TavernCardParser.parseRawJson(FileInputStream(file))
        println("rawJson2 is null? ${rawJson == null}")
        if (rawJson != null) {
            println("rawJson2 length: ${rawJson.length}")
        }

        val charaData = TavernCardParser.parse(FileInputStream(file))
        println("charaData2 is null? ${charaData == null}")
        if (charaData != null) {
            println("charaData2 Name: ${charaData.name}")
        }
        
        assertNotNull(rawJson)
        assertNotNull(charaData)
    }
}
