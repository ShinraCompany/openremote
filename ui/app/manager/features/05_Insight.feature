@OpenRemote @Insgith
Feature: Insight

    Background: Navigation
        Given Login OpenRemote as "smartcity"
    
     @Desktop @insert_attribute
     Scenario Outline: Insert attributes
        Given Nevigate to "Insight" tab
        When Select "<attribute>" from "<asset>"
        Then We should see the graph

        Examples:
            | attribute    | asset    | 
            | Energy level | Battery  | 
            | Power        | Solar    |