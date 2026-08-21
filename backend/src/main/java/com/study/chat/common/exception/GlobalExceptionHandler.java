package com.study.chat.common.exception;


import com.study.chat.common.result.Result;


import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;


@RestControllerAdvice
public class GlobalExceptionHandler {



    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> validation(
            MethodArgumentNotValidException e
    ){


        String message =
                e.getBindingResult()
                        .getFieldErrors()
                        .get(0)
                        .getDefaultMessage();


        return Result.error(
                400,
                message
        );

    }



    @ExceptionHandler(Exception.class)
    public Result<?> error(Exception e){

        e.printStackTrace();

        return Result.error(
                500,
                "服务器异常"
        );
    }

}