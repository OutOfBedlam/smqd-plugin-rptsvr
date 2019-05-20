package com.thing2x.rptsvr.api

import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, SecretKey}
import org.scalatest.FlatSpec

class JSPasswordDecTest extends FlatSpec {

  // get key info from "passwordEncoder" in "jasperserver-pro/WEB-INF/applicationContext-security.xml"
  val secretKey = "0xC8 0x43 0x29 0x49 0xAE 0x25 0x2F 0xA1 0xC1 0xF2 0xC8 0xD9 0x31 0x01 0x2C 0x52 0x54 0x0B 0x5E 0xEA 0x9E 0x37 0xA8 0x61"
  val initVector = "0x8E 0x12 0x39 0x9C 0x07 0x72 0x6F 0x5A"
  val secretKeyAlgorithm = "DESede"
  val cipherTransformation = "DESede/CBC/PKCS5Padding"

  val message = "1879B779A25FDFF4"

  def secretKeyBytes: Array[Byte] = {
    secretKey.split(" ").toSeq.map { tok =>
      val hex = tok.substring(2)
      Integer.parseInt(hex, 16).toByte
    }.toArray
  }

  def initVectorBytes: Array[Byte] = {
    initVector.split(" ").toSeq.map { tok =>
      val hex = tok.substring(2)
      Integer.parseInt(hex, 16).toByte
    }.toArray
  }

  private def dehexify(data: String): Array[Byte] = {
    val bytes = new Array[Byte](data.length/2)
    data.grouped(2).zipWithIndex.foreach { case(ch, idx) =>
      bytes(idx) = Integer.parseInt(ch, 16).toByte
    }
    bytes
  }

  def messageBytes: Array[Byte] = {
    dehexify(message)
  }

  def valueOf(buf: Array[Byte]): String = buf.map("%02X " format _).mkString

  "password decryption" should "decrypt" in {
    val keyBytes = secretKeyBytes
    val ivBytes = initVectorBytes
    val valBytes = messageBytes

    println(s"key len: ${keyBytes.length}  : ${valueOf(keyBytes)}")
    println(s"val len: ${valBytes.length}  : ${valueOf(valBytes)}")

    val key: SecretKey = new SecretKeySpec(keyBytes, secretKeyAlgorithm)
    val iv: IvParameterSpec = new IvParameterSpec(ivBytes)
    val cipher: Cipher = Cipher.getInstance(cipherTransformation)
    cipher.init(Cipher.DECRYPT_MODE, key, iv)
    val plainTextBytes = cipher.doFinal(valBytes)
    val plainText = new String(plainTextBytes, "UTF-8")
    println(s"============> $plainText")
    assert(plainText == "csweb")
  }

}
