package tokyo.peya.obfuscator.ui;

import java.util.Random;
import java.util.Scanner;

public class Main
{
    public static final int MAX_NUMBER = 100;

    public static void main(String[] args)
    {
        String helloWorld = "Hello, World!";

        while (true)
        {
            int randomNumber = new Random().nextInt(MAX_NUMBER) + 1;
            System.out.println("Random number is " + randomNumber);
            System.out.print("Enter a number between 1 and 100: ");
            String userInputString = getUserInput();
            int userInput = parseInt(userInputString);
            if (userInput < 1 || userInput > MAX_NUMBER)
            {
                System.out.println("Please enter a number between 1 and 100.");
                continue;
            }

            if (userInput == randomNumber)
            {
                System.out.println("You guessed the number!");
                break;
            }
            else
            {
                System.out.println("Try again!");
                // noinspection ALL
                continue;
            }
        }

        System.out.println(helloWorld);
    }

    public static String getUserInput()
    {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a string: ");
        return scanner.nextLine();
    }

    public static int parseInt(String input)
    {
        try
        {
            return Integer.parseInt(input);
        }
        catch (NumberFormatException e)
        {
            System.out.println("Invalid input. Please enter a number.");
            return -1;
        }
    }

}
