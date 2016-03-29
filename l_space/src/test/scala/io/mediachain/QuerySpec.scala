package io.mediachain

import io.mediachain.Types._
import io.mediachain.util.orient.MigrationHelper
import org.specs2.Specification
import gremlin.scala._
import scala.util.Random
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

object QuerySpec extends BaseSpec
  with ForEach[GraphFixture.Context] {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}


  // TODO: can you figure out how to abstract out the connection creation?
  def foreach[R: AsResult](f: GraphFixture.Context => R): Result = {

    lazy val graph = MigrationHelper.newInMemoryGraph()
    try {
      AsResult(f(GraphFixture.Context(graph)))
    } finally {
      graph.database().drop()
    }
  }

  def is =
  s2"""
  Given a MetadataBlob, find the Canonical $findsPhoto
  Given a Canonical, finds full tree $findsTree
  Given a Person, finds the person's Canonical $findsPerson
  Given a MetadataBlob, finds the author Person $findsAuthor
  Given a Person, finds all Canonical that they are the Author of $findsWorks

  Does not find a non-matching ImageBlob $doesNotFindPhoto
  """

  // TESTS BELOW
  def findsPhoto = { context: GraphFixture.Context =>
    val queriedCanonical = Query.findImageBlob(context.graph, context.objects.imageBlob)

    queriedCanonical must beRightXor({ (c: Canonical) =>
      c.canonicalID == context.objects.imageBlobCanonical.canonicalID
    })
  }

  def findsTree = { context: GraphFixture.Context =>
    val tree = Query.findTreeForCanonical(context.graph, context.objects.imageBlobCanonical)
    tree.foreach(t => println(t.V.toList()))
    tree must beRightXor { (g: Graph) =>
      // Canonical itself
      (context.objects.imageBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "canonical" must beSome) and
      // ImageBlob
      (context.objects.imageBlob.id.flatMap(id => g.V(id).headOption) aka "describing photoblob" must beSome) and
      // Modifying ImageBlob
      (context.objects.modifiedImageBlob.id.flatMap(id => g.V(id).headOption) aka "modifying photoblob" must beSome) and
      // Person
      (context.objects.person.id.flatMap(id => g.V(id).headOption) aka "person" must beSome) and
      // Person canonical
      (context.objects.personCanonical.id.flatMap(id => g.V(id).headOption) aka "person canonical" must beSome) and
      // Another photoblob by same author
      (context.objects.extraImageBlob.id.flatMap(id => g.V(id).headOption) aka "extra photoblob" must beNone) and
      // Another photoblob's canonical
      (context.objects.extraImageBlobCanonical.id.flatMap(id => g.V(id).headOption) aka "extra canonical" must beNone)
    }
  }

  def findsPerson = { context: GraphFixture.Context =>
    val queriedCanonical = Query.findPerson(context.graph, context.objects.person)

    queriedCanonical must beRightXor { (person: Canonical) =>
      (person.canonicalID must_== context.objects.personCanonical.canonicalID) and
        (person.id must beSome)
    }
  }

  def findsAuthor = { context: GraphFixture.Context =>
    val queriedAuthor =
      Query.findAuthorForBlob(context.graph, context.objects.imageBlob)

    queriedAuthor must beRightXor { (c: Canonical) =>
      c.canonicalID must_== context.objects.personCanonical.canonicalID
    }
  }

  def findsWorks = { context: GraphFixture.Context =>
    val queriedWorks = Query.findWorks(context.graph, context.objects.person)

    queriedWorks must beRightXor { (s: List[Canonical]) =>
      s must contain(context.objects.imageBlobCanonical)
    }
  }

  def doesNotFindPhoto = { context: GraphFixture.Context =>
   val queryBlob = context.objects.imageBlob.copy(
      description = GraphFixture.Util.mutate(context.objects.imageBlob.description))

    val queriedPhoto = Query.findImageBlob(context.graph, queryBlob)

    queriedPhoto must beLeftXor
  }

  def findsCanonicalForModifiedBlob = { context: GraphFixture.Context =>

    val parentCanonical = Query.findImageBlob(context.graph, context.objects.imageBlob)
    val childCanonical = Query.findCanonicalForBlob(context.graph, context.objects.modifiedImageBlob)
    (childCanonical must beRightXor) and
      (childCanonical must_== parentCanonical)
  }


}
