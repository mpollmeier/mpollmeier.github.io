      SELECT * FROM METHOD
      JOIN CALL ON METHOD.id = CALL.target_method_id
      JOIN CALL_ARG_IDX ON CALL_ARG_IDX.call_id = CALL.id
      JOIN EXPRESSION ON CALL_ARG_IDX.expression_id = EXPRESSION.id
      WHERE METHOD.name = 'java.sql.Statement.execute';



      SELECT * FROM METHOD method1
      JOIN CALL call1 ON method1.id = call1.target_method_id
      JOIN CALL_ARG_IDX ON CALL_ARG_IDX.call_id = call1.id
      JOIN EXPRESSION 
        ON CALL_ARG_IDX.expression_id = EXPRESSION.id
        AND EXPRESSION.type = 'CALL'
      JOIN CALL call2 ON EXPRESSION.call_id = call2.id
      JOIN METHOD method2 ON call2.target_method_id = method2
      WHERE method1.name = 'java.sql.Statement.execute';


  SELECT 'giving up';
