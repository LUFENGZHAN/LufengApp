package com.study.chat.common.resolver;


import com.study.chat.common.annotation.RequestDTO;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;


import lombok.RequiredArgsConstructor;


import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectMapper;


import java.util.Map;



@Component
@RequiredArgsConstructor
public class RequestDTOResolver implements HandlerMethodArgumentResolver {


    private final ObjectMapper objectMapper;



    @Override
    public boolean supportsParameter(
            MethodParameter parameter
    ){

        return parameter.hasParameterAnnotation(RequestDTO.class);

    }



    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) throws Exception {


        HttpServletRequest request =
                webRequest.getNativeRequest(HttpServletRequest.class);



        Class<?> clazz =
                parameter.getParameterType();



        Object target;



        String contentType =
                request.getContentType();



        /**
         * JSON
         */
        if(contentType != null &&
                contentType.contains("application/json")){


            target =
                    objectMapper.readValue(
                            request.getInputStream(),
                            clazz
                    );


        }
        /**
         * form
         */
        else {


            target =
                    clazz.getDeclaredConstructor()
                            .newInstance();



            WebDataBinder binder =
                    binderFactory.createBinder(
                            webRequest,
                            target,
                            parameter.getParameterName()
                    );


            Map<String,String[]> params =
                    request.getParameterMap();


            binder.bind(
                    new org.springframework.beans.MutablePropertyValues(params)
            );


        }



        /**
         * 参数校验
         */
        if(parameter.hasParameterAnnotation(Valid.class)){


            WebDataBinder binder =
                    binderFactory.createBinder(
                            webRequest,
                            target,
                            parameter.getParameterName()
                    );


            binder.validate();


            BindingResult result =
                    binder.getBindingResult();



            if(result.hasErrors()){

                throw new MethodArgumentNotValidException(
                        parameter,
                        result
                );

            }

        }


        return target;

    }

}