package com.example.employee.service;

import com.example.employee.entity.Employee;
import com.example.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    @Autowired
    private final EmployeeRepository repository;

    /**
     * Method that saves a new employee to the database.
     * 
     * @param employee to be saved.
     * @return the saved employee.
     */
    public Employee createEmployee(Employee employee) {
        return repository.save(employee);
    }

    /**
     * Method that retrieves all employees from the database.
     * 
     * @return a list of all employees.
     */
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    /**
     * Method that deletes an employee by their ID.
     * 
     * @param id of the employee to be deleted.
     * @throws EntityNotFoundException if no employee with the given ID is found.
     */
    public void deleteEmployee(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("There is no employee with that ID");
        }
        repository.deleteById(id);
    }

    /**
     * Method that retrieves an employee by their ID.
     * 
     * @param id of the employee to be retrieved.
     * @return the employee with the given ID or null if not found.
     */
    public Employee getEmployeeById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Method that updates an existing employee.
     * 
     * @param id       of the employee to be updated.
     * @param employee details to update.
     * @return the updated employee or null if the employee with the given ID is not
     *         found.
     */
    public Employee updateEmployee(Long id, Employee employee) {
        Optional<Employee> opEmployee = repository.findById(id);
        if (opEmployee.isPresent()) {
            Employee curEmployee = opEmployee.get();
            curEmployee.setName(employee.getName());
            curEmployee.setFamily(employee.getFamily());
            curEmployee.setEmail(employee.getEmail());
            curEmployee.setPhoneNum(employee.getPhoneNum());
            return repository.save(curEmployee);
        }
        return null;
    }
}
