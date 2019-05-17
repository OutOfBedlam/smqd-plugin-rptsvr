package com.thing2x.rptsvr.api

import java.util.Base64

import javax.crypto.{Cipher, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.xml.bind.DatatypeConverter
import org.scalatest.FlatSpec

class JSPasswordDecTest extends FlatSpec {

  // get key info from "passwordEncoder" in "jasperserver-pro/WEB-INF/applicationContext-security.xml"
  val secretKey = "0xC8 0x43 0x29 0x49 0xAE 0x25 0x2F 0xA1 0xC1 0xF2 0xC8 0xD9 0x31 0x01 0x2C 0x52 0x54 0x0B 0x5E 0xEA 0x9E 0x37 0xA8 0x61"
  val secretKeyAlgorithm = "DESede"
  val cipherTransformation = "DESede/CBC/PKCS5Padding"

  val message = "1879B779A25FDFF4"
//val message = "774FF601BAC2B606A6A123A3BBC7A060"
  //val message = "C893BF265815FA49"

  def secretKeyBytes: Array[Byte] = {
    val k = secretKey.split(" ").toSeq.map { tok =>
      val hex = tok.substring(2)
      Integer.parseInt(hex, 16).toByte
    }.toArray

    //k.slice(0, 8) ++ k.slice(8, 16) ++ k.slice(0, 8)
    k
  }

  def messageBytes: Array[Byte] = {
    //Base64.getDecoder.decode(message)
    DatatypeConverter.parseHexBinary(message)
  }

  def valueOf(buf: Array[Byte]): String = buf.map("%02X " format _).mkString

  "password decryption" should "decrypt" in {
    val keyBytes = secretKeyBytes
    val valBytes = messageBytes

    println(s"key len: ${keyBytes.length}  : ${valueOf(keyBytes)}")
    println(s"val len: ${valBytes.length}  : ${valueOf(valBytes)}")

    val key: SecretKey = new SecretKeySpec(keyBytes, secretKeyAlgorithm)
    val iv: IvParameterSpec = new IvParameterSpec(new Array[Byte](8))
    val cipher: Cipher = Cipher.getInstance(cipherTransformation)
    cipher.init(Cipher.DECRYPT_MODE, key, iv)
    //val plainText = cipher.doFinal(valBytes)
    //println(s"============> ${new String(plainText, "UTF-8")}")
  }

}
