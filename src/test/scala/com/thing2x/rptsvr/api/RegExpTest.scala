package com.thing2x.rptsvr.api

import org.scalatest.FlatSpec

class RegExpTest extends FlatSpec {

  val reg =  """(\S+).(pdf|html)""".r

  "reg" should "work" in {
    reg.findFirstMatchIn("/myplace/myfile.pdf") match {
      case Some(tok) => println(s"===> ${tok.group(1)}   ${tok.group(2)}")
    }
  }
}
