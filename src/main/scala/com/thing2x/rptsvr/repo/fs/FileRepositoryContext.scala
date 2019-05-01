// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat

import akka.stream.Materializer
import com.thing2x.rptsvr.{Repository, RepositoryContext}

import scala.concurrent.ExecutionContext

class FileRepositoryContext(
                             val repository: Repository,
                             val executionContext: ExecutionContext,
                             val materializer: Materializer,
                             val root: File,
                             val dateFormat: SimpleDateFormat,
                             val datetimeFormat: SimpleDateFormat) extends RepositoryContext
