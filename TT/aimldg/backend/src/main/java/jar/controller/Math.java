package jar.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Math {

    @PostMapping("/math/{n}")
    Map<Object, Object> m1(@PathVariable int n) {

        Map<Object, Object> res = new HashMap<>();
        List<Object> l = new ArrayList<>();

        l.add(0);
        l.add(1);
        res.put("msg", "welcome to math api end point");
        res.put("status", 200);
        Math obj = new Math();
        res.put(n, obj.fib(n));
        // res.put("list",l);
        for (int i = 2; i <= n; i++) {
            l.add(obj.fib(i));
        }
        res.put("fibseq...", l);

        return res;
    }

    int fib(int n) {
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int c = a + b;
            a = b;
            b = c;
        }
        return b;
    }

}
