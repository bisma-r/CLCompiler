startProgram
    variables:
        int num = 4;
        int fact = 1;
    code:
        loopif num > 0 holds
            fact = fact * num;
            num = num - 1;
        endloop
        outString(fact);
endProgram
