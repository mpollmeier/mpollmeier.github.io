# 2 intro
Hi everyone, welcome to this session on graph databases for code analysis! 

A bit of context for this talk: at ShiftLeft, we develop code analysis tools to find security vulnerabilities, data leaks etc. And we also provide tools like joern and ocular, which let you interactively explore code and find patterns yourself. In this session we'll go through how the technology behind that works on a high level. 

# few words about myself
My name is Michael Pollmeier, I'm a software developer in the Code Science Team. And I love Scala and Graph Databases and started/maintain some open source projects that received some interest in the community, most importantly gremlin-scala and overflowdb. 

You can also find me on twitter, if you're interested in the above, plus the odd travel picture from New Zealand...

# 3 what you'll learn today
My goal for today is to give you an introduction to graph databases and how they compare to relational databases. 
We'll look at how you'd model a particular domain - we'll use an excerpt of the code property graph.
And then we'll see how to write a few graph traversals to find a common and widely known vulnerability, a SQL injection. 

It certainly helps if you have some basic knowledge of any query language. Most folks learnt SQL at some point. But this isn't essential to be able to follow this talk. 

# 4 what is a graph database?
A graph database quite simplistic at it's core: there are only nodes and edges between nodes. Both nodes and edges have labels and properties. Edges are directed. And voila, that's all there is to call it a property graph database. 

Labels are similar to tables in relational databases, but depending on the graph db they often provide more flexibility - think of them as just a loose coupling of nodes.

The biggest difference to relational databases is that edges are first class citizens. 
I.e. Graph databases treat the relationships between data as equally important to the data itself.
In relational databases, relationships are actually expressed via lookup columns (for 1:1) or even lookup tables (1:m), which makes it a heck more complicated.

Let's now take a look how you'd model a domain with a graph db and with a relational db. We'll use code analysis as our example. 

# 5 domain model: graph db
Ok so here's our code analysis domain model for a graph database. There are methods (top left), which have properties like name, signature etc. And there are calls, i.e. method invocations, which 'target' method via one edge. And those calls pass 0 or many parameters to the method invocation. Those parameters are generic expressions, and depending on their label it could be a literal, or yet another method call. 

I'd argue that this is pretty much exactly like you'd sketch it on a whiteboard. That's the beauty of graph models - that's what I mean when I say: they are compatible with the human mind. 

For comparison, let's see how you'd model this in a relational database, i.e. with tables. 

# 6: domain model: relational db
Left side: graph db, right side: relational db. 
A few things are identical, e.g. there are methods with name/signature/... properties. 

Now let's focus on the differences: 
1) target edge (call -> method): separate column in call table (1:1 relationship). fairly simple because each call has exactly one target method.

2) argument edge (call -> expression): 1:many relationship, therefor we need a separate lookup table: call_arg_idx. That separate table is also where we store the argument index. 
Bottom line: when it comes to modelling relationships, relational dbs are more complicated, and they make you use use different techniques depending on the cardinality of the relationship. 
You get used to this after you modeled your first few domains, but if you take a step back and think about it, it's not very intuitive. You need to bend your mind. 

3) Expression: this is one way to model it: lookup table with all expression ids with expression label: literal, call, ... and a link to the 'actual' expression id. 
Alternatively, we could cram all expression types into one table with wide rows. Or we could ensure we're using one global id pool for all expressions, be it literals, calls, etc. Or, or or. 
Bottom line: graph labels are more flexible than tables. 

Ok, so this was an overview of the differences in modelling between graph and relational databases. 
Next, let's see what that means for querying the data. 
We will build up a traversal for finding a sql injection with both a graph traversal and a sql query. To make it easier to follow we'll do that in a few iterations over the next few slides. 

# 7: 1) Method call, passing a literal as argument - graph
We start with this simple piece of code: there's a string literal called 'sql' which is being passed to a database. 
For your understanding: in code analysis, our starting position is that we already know that certain methods are interesting. In this case we know that `Statement.execute` is worth looking at, since it's arguments are ultimately passed down to a database. 

In this example a simple string literal is passed down. So how do we find this code pattern using a graph traversal for our code analysis domain? Here's an example query based on overflowdb's query language, which is very similar to gremlin, if you've heard about that one before. 
graph: of all method nodes, find the one(s) that has the name 'Statement.execute'. 
Then follow the incoming TARGET edge to find it's method inocations, or CALLs
From the call, follow the outgoing ARGUMENT edge(s), and voila we are at the expression, in this case a simple string literal.

Now let's see how we can do the same thing in SQL

# 8: 1) Method call, passing a literal as argument - relational
So now the same thing in a SQL query, i.e. how can we find the method we're interested in and figure out what it's being called with? The relational schema from before is at the right. The graph traversal is still there for reference. 
The SQL query (bottom left, in brown background) is essentially is doing the same thing: find the method with that particular name, find the method invocations and find it's arguments. 

But in order to find those relationships, we have to join the call table, join the call_arg_idx table, join the expression table etc.
This is much longer, mostly because we need to specify which ids to match when joining a table at query time. 
  -> relationships are reified at query time, which is wordy and error prone. that's interesting, because the strong point of declarative query languages is usually that you declare WHAT you want, not HOW to get there. In SQL, you actually need to take care of both. 
  -> error prone: easy to lookup the wrong id
  -> more text makes it harder to read
  -> 'select from method' and 'where method.name' are not directly together, they're actually at the opposite ends of the traversal

Let's modify our code example a little bit. 

# 9: 2: Call argument is another method call - graph
Instead of the string literal, we are now passing the input from a servlet directly to the database. This is quite obviously a sql injection - so let's check how we'd query that in our domain, first with a graph traversal.

Graph: just like before: of all method nodes, find the one(s) hat has the name 'Statement.execute'. 
Then follow the incoming TARGET edge to see where it's being called. 
From the call, follow the outgoing ARGUMENT edge(s), and just like before, we are at the expression
differently to the first case, we're now only interested in arguments that have the CALL label
From that call we can traverse to the method that's being invoked and we've reached servlet.inputParameter

Hopefully you can see the pattern: we simply follow the edges from our domain model.

Now let's see how we can do the same thing in SQL

# 10: 2: Call argument is another method call - relational
So here's the same for relational dbs. Again, the model on the right. On the left we have the code example, the the graph traversal from the last slide, and the SQL statement. 
Just like in the graph traversal, we now only want to have CALLs and want to join back to the method table. 

So this slight change in the query brings the complexity in SQL to the next level. I don't even want you to try and understand this query: it's very hard to follow what's going on, also since we need to join the same table multiple times and give them different aliases. Compare that to the graph traversal where we simply describe which outgoing/incoming edges we want to follow.

Enough already? Well, the code we're analysing is still far too simplistic to be found in real life. No one in their good mind sends the SQL statement from the client to the server. Instead, real SQL injections stem from user input that goes through an arbitrary number of method calls, and finally ends up in something like `Statement.execute`. Let's modify our example further. 

# 11: 3: Chain of method calls of arbitrary length
Our example code just became a bit longer, but essentially we just added one indirection: we have a string literal which contains half a SQL query. And we have the user input which comes from a servlet, like before, so that's a method call. Both are passed through one method. How would we find something like that in a graph traversal? And what if there's 10 or 100 additional indirections, as it happens?

Again, we're starting with a lookup of methods with the name `Statement.execute` and traverse to where they are called. 
Now here comes the new part: the `repeat` step! We give it a subtraversal that it will repeat, in this case traverse to the argument expression, where we're only interested in calls. 
In this example the repeat step will continue following these method invocations, until we found the `Servlet.inputParameter` call.

So what have we done here? We have some prior knowledge about interesting methods, and want to see if they are connected via method calls. The graph traversal is very expressive about what it does. 
Something like `repeat` exists in all major graph traversal languages, but not in SQL. 
If you try the same with a relational database, you'd probably have to handle the 'repeat' logic inside your application logic, going between the application and database back and forth. 

Bottom line: graph databases are designed for finding connections between nodes, even if they are of arbitrary length. SQL in comparison is very noisy because it makes you define both WHAT you want and HOW you get it, every time. And it doesn't support the 'arbitrary length' part at all.

# 12: silver bullet?
As always: choose the right tool for the job. for code analysis and similar highly-connected domains, graph dbs work really well
Obviously the db modelling and query language are just two factors in choosing a technology stack - there are others. 
Sql and relational dbs are years of research and wide adoption ahead, have proper standards and committees etc. 
Databases and tooling not as mature as Relational DBs - still a niche technology. It's fun to be off the beaten track though!
ShiftLeft is actively changing this - we are developing everything graph related as open source: overflowdb

# 13: Summary
Key takeaway points: graph databases treat the relationships just as important as the data itself. That's why we have edges as first class citizens. Modelling a graph schema is just like you'd draw it on a whiteboard. 
When you traverse a graph, you don't need to line up IDs in every query like with SQL. You just specify which edges you want to follow, which makes graph traversals very expressive. And you get additional gimmicks like the `repeat` step. 

I hope you got some insight into why and how we use a graph database at ShiftLeft. If you're a joern joern or ocular user, you can explore the code property graph interactively, so this background will hopefully help as well.

# 14: q&a
I believe we've got x minutes for a few questions, if there are any in the chat. 

# 15: thanks
Thanks for joining this sesssion! Back to you, Alok!


# question for myself if no one asks anything:
  * Can i use overflowdb for my webshop? 
    That's a great question, thank you! :)
    I wouldn't recommend it at this stage. If you're not doing anything fancy with relationships, you probably won't benefit enough from the features a graph db gives you, to make up for the lack of maturity.
  * what i mean by lack of maturity: 
    * deployment models, upgrades/downgrades, transactions, tooling, stack overflow, industry knowledge
