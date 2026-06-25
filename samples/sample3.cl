startProgram
    variables:
        int x = 2;
        int result = 0;
        float pi = 3.14;
        string msg = "hello";
        char grade = 'A';
    code:
        switchFor (x)
            case 1 : result = result + 10;
            case 2 : result = result + 20;
            other  : result = result + 99;
        endswitchFor
        outString(result);
        outString(pi);
endProgram
