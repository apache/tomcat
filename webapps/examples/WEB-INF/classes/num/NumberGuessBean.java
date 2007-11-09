/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/*
 * Originally written by Jason Hunter, http://www.servlets.com.
 */

package num;

import java.util.*;

public class NumberGuessBean {

  int answer;
  boolean success;
  String hint;
  int numGuesses;

  public NumberGuessBean() {
    reset();
  }

  public void setGuess(String guess) {
    numGuesses++;

    int g;
    try {
      g = Integer.parseInt(guess);
    }
    catch (NumberFormatException e) {
      g = -1;
    }

    if (g == answer) {
      success = true;
    }
    else if (g == -1) {
      hint = "a number next time";
    }
    else if (g < answer) {
      hint = "higher";
    }
    else if (g > answer) {
      hint = "lower";
    }
  }

  public boolean getSuccess() {
    return success;
  }

  public String getHint() {
    return "" + hint;
  }

  public int getNumGuesses() {
    return numGuesses;
  }

  public void reset() {
    answer = Math.abs(new Random().nextInt() % 100) + 1;
    success = false;
    numGuesses = 0;
  }
}
