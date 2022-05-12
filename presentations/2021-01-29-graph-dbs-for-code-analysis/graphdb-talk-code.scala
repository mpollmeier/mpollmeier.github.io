  graph.nodes("METHOD")
       .has("name", "Statement.execute")
       .in("TARGET") // -> CALL
       .out("ARGUMENT")

  // same with domain specific traversals (embedded dsl)
  graph.method
   .name("Statement.execute")
   .calledBy
   .argument


  graph.nodes("METHOD")
       .has("name", "Statement.execute")
       .in("TARGET")     // -> CALL
       .out("ARGUMENT")
       .hasLabel("CALL") // only CALLs
       .out("TARGET")    // -> METHOD

  // same with domain specific traversals (embedded dsl)
  graph.method
   .name("Statement.execute")
   .calledBy
   .argument
   .isCall  
   .target  

//with repeat:
  graph.nodes("METHOD")
    .has("name", "Statement.execute")
    .in("TARGET") // -> CALL
    .repeat(
      _.out("ARGUMENT").hasLabel("CALL"))
    (_.until(_.out("TARGET").has("name", "Servlet.inputParameter")))


  // same with domain specific traversals (embedded dsl)
  graph.method.name("java.sql.Statement.execute")
    .calledBy
    .repeat(_.argument.isCall)(
      _.until(_.target.name("Servlet.inputParameter"))
