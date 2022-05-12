
  // in class java.sql.Statement
  public boolean execute(String sql); 

  String sql = "drop from users;";
  statement.execute(sql);



       String sql = "delete from users";
       statement.execute(sql);

       String sql = servlet.inputParameter("sql");
       statement.execute(sql);


        public String concatenate(String a, String b) {
          return a + b;
        }
        String select = "select * from user where name = ";
        String user   = servlet.inputParameter("user");
        String sql    = concatenate(select, user);
        statement.execute(sql);
