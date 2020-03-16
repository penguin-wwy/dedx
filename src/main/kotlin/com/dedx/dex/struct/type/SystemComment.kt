/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.dex.struct.type

object DalvikAnnotationDefault : ObjectType("dalvik.annotation.AnnotationDefault")

object DalvikEnclosingClass : ObjectType("dalvik.annotation.EnclosingClass")

object DalvikEnclosingMethod : ObjectType("dalvik.annotation.EnclosingMethod")

object DalvikInnerClass : ObjectType("dalvik.annotation.InnerClass")

object DalvikMethodParameters : ObjectType("dalvik.annotation.MethodParameters")

object DalvikSigature : ObjectType("dalvik.annotation.Signature")

object DalvikThrows : ObjectType("dalvik.annotation.Throws")

fun isSystemCommentType(other: ObjectType) = (other == DalvikAnnotationDefault) or
        (other == DalvikEnclosingClass) || (other == DalvikEnclosingMethod) or
        (other == DalvikInnerClass) || (other == DalvikMethodParameters) or
        (other == DalvikSigature) || (other == DalvikThrows)
