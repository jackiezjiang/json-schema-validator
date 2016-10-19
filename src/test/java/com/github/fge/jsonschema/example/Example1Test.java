
package com.github.fge.jsonschema.examples;
import java.io.IOException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
public  class Example1Test

{

 public static void main(String[] args)
        throws IOException, ProcessingException {
         Example1.class.getMethod("main",  String[].class).invoke(null, new Object[]{new String[]{}});
           
        }
}


