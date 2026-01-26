Feature: Hello World

  Scenario: Foo
    Given one

  Scenario Outline: Foo
    Given one
    Examples: Foooo
      | foo |
      | 1   |

    Examples: Bar
      | foo |
      | 1   |

  Scenario Outline: Lalala
    Given value is "<val>"
    Examples:
      | val |
      | 1   |
      | 2   |