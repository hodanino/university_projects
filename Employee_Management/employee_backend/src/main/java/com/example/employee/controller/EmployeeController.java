package com.example.employee.controller;

import com.example.employee.entity.Employee;
import com.example.employee.service.EmployeeService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin("*")
public class EmployeeController {
    @Autowired
    private EmployeeService service;

    /**
     * Endpoint to create a new employee.
     * 
     * @param employee the employee to be created.
     * @return the created employee.
     */
    @PostMapping("/employee")
    public Employee createEmployee(@RequestBody Employee employee) {
        return service.createEmployee(employee);
    }

    /**
     * Endpoint to get all employees.
     * 
     * @return a list of all employees.
     */
    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        return service.getAllEmployees();
    }

    /**
     * Endpoint to delete an employee by their ID.
     * 
     * @param id the ID of the employee to be deleted.
     * @return a response entity indicating the result of the operation.
     */
    @DeleteMapping("/employee/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        try {
            service.deleteEmployee(id);
            return new ResponseEntity<>("Employee deleted", HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Endpoint to get an employee by their ID.
     * 
     * @param id the ID of the employee to be retrieved.
     * @return the employee with the given ID or a not found status if not found.
     */
    @GetMapping("/employee/{id}")
    public ResponseEntity<?> getEmployeeById(@PathVariable Long id) {
        Employee employee = service.getEmployeeById(id);
        if (employee == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(employee);
    }

    /**
     * Endpoint to update an existing employee.
     * 
     * @param id       the ID of the employee to be updated.
     * @param employee the new employee details.
     * @return the updated employee or a bad request status if the employee is not
     *         found.
     */
    @PatchMapping("/employee/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody Employee employee) {
        Employee updatedEmployee = service.updateEmployee(id, employee);
        if (updatedEmployee == null)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        return ResponseEntity.ok(updatedEmployee);
    }
}
