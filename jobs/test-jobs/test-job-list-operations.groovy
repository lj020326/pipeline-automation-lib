#!/usr/bin/env groovy

//@groovy.transform.Sortable
//@groovy.transform.ToString


class User {
    String username, email
    int id
}

// ref: https://issues.jenkins-ci.org/browse/JENKINS-44924
// ref: https://stackoverflow.com/questions/46335062/list-in-place-sorting-in-jenkins-pipelines
@NonCPS
def nonCpsTest() {

    // ref: http://docs.groovy-lang.org/next/html/documentation/working-with-collections.html
    echo "testing simple sort operations..."

    [1, 2, 3].each {
        echo "Item: $it" // `it` is an implicit parameter corresponding to the current element
    }

    echo "**** iterate over sorted list0:"
    ['a', 'b', 'c'].eachWithIndex { it, i -> // `it` is the current element, while `i` is the index
        echo "$i: $it"
    }

    def list = ['abc', 'z', 'xyzuvw', 'Hello', '321']

    assert list.sort {
        it.size()
    }

    echo "**** iterate over sorted list:"
    list.eachWithIndex { it, i -> // `it` is the current element, while `i` is the index
        echo "$i: $it"
    }

    //assert list.sort {
    //    it.size()
    //} == ['z', 'abc', '321', 'Hello', 'xyzuvw']


    def list2 = [6, -3, 9, 2, -7, 1, 5]

    list2.sort {x,y->
        x <=> y
    }


    echo "**** iterate over sorted list2:"
    list2.eachWithIndex { it, i -> // `it` is the current element, while `i` is the index
        echo "$i: $it"
    }

    //assert list2.sort {x,y->
    //    x.order <=> y.order
    //} == [7, -3, 1, 2, 5, 6, 9]

    // ref: https://dzone.com/articles/groovy-goodness-new-methods
    echo "testing more sort operations..."

    def mrhaki1 = new User(username: 'mrhaki', email: 'mrhaki@localhost', id: 0)
    def mrhaki2 = new User(username: 'mrhaki', email: 'user1@localhost', id: 5)
    def hubert1 = new User(username: 'hubert', email: 'user2@localhost', id: 2)
    def hubert2 = new User(username: 'hubert', email: 'hubert@localhost', id: 3)


    // We make the list immutable,
    // so we check the toSorted and toUnique methods
    // do not alter it.
    //def users = [mrhaki1, mrhaki2, hubert1, hubert2].asImmutable()
    def users = [mrhaki1, mrhaki2, hubert1, hubert2]
    def origUsers = users.clone()

    // toSorted
    //def sortedUsers = users.toSorted()
    users.sort{it.username}

    // @Sortable adds a compareTo method
    // to User class to sort first by username
    // and then email.
//    assert users == [hubert2, hubert1, mrhaki1, mrhaki2]
    assert users == [hubert1, hubert2, mrhaki1, mrhaki2]

    // Original list is unchanged.
    assert origUsers == [mrhaki1, mrhaki2, hubert1, hubert2]

    // Use toSorted with closure.
//    def sortedByEmail = users.toSorted { a, b -> a.email <=> b.email }
    def sortedByEmail = users.clone()
    sortedByEmail.sort { a, b -> a.email <=> b.email }
    assert sortedByEmail == [hubert2, mrhaki1, mrhaki2, hubert1]

    echo "**** tests for copy list"
    // ref: https://stackoverflow.com/questions/22586892/how-to-copy-a-list-in-groovy
    def list3 = [1, 2, 4]

    //by value
    def clonedList = list3.clone() //or list.collect()
    assert clonedList == list3
//    assert !clonedList.is(list3) //Reference inequality

    // pop/remove not available so skip the following tests
////    list3.pop() //modify list
//    list3.remove() //modify list
//
//    assert clonedList == [1, 2, 4]
//    assert list3 == [1, 2]
//
//    //by reference
//    def anotherList = list3
//    assert anotherList == [1, 2]
////    assert anotherList.is(list3) //Reference equality
//
////    list3.pop() //modify again
//    list3.remove() //modify again
//
//    assert list3 == [1]
//    assert anotherList == [1]

}

node{
   nonCpsTest()
}
