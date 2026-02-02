Feature: Hello World

  Scenario: Foo
    Given one

  Scenario: Fail
    Given fail

  Scenario: Fail 2
    Given fail

  Scenario Outline: Foo
    Given one
    Examples: Foooo
      | foo |
      | 1   |

    Examples: Bar
      | foo |
      | 1   |

  Scenario Outline: Lalala <val>
    Given value is "<val>"
    Examples:
      | val |
      | 1   |
      | 2   |